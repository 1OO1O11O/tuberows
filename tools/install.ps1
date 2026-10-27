# TizenTube Recommendations for Projectivy Launcher - Windows installer
#
# Run it either way:
#   - Paste this whole file into PowerShell and press Enter, or
#   - powershell -ExecutionPolicy Bypass -File .\install.ps1
#
# What it does (nothing else):
#   1. Finds adb, or offers to download Google's official platform-tools to %LOCALAPPDATA%\Android
#   2. Downloads the latest release APK from GitHub (or uses $ApkPath) and checks its SHA-256
#   3. Connects to your TV over Wireless debugging (direct connect, then pairing if needed)
#   4. Installs the app, grants READ_TV_LISTINGS, and exempts it and SmartTube from battery optimisation
#   5. Opens the app once and reports what it found
# It does not change your PATH, registry, or anything else on this PC.

& {
    # ---- Settings (edit if you like; blank values are asked for or detected) ----
    $TvAddress       = ''      # TV IP, or IP:port from Wireless debugging. Blank = use a connected TV or ask.
    $ApkPath         = ''      # Path to a local APK. Blank = download the latest GitHub release.
    $BatteryExempt   = $true   # Exempt this app and SmartTube from battery optimisation (recommended).
    $SilentExecution = $false  # No prompts: take defaults, fail instead of asking.
    $VerboseOutput   = $false  # Show adb output for each step.
    $LogPath         = Join-Path $env:TEMP 'TizenTubeRecommendations\install.log'

    $Repo       = '1OO1O11O/tuberows'
    $AppId      = 'tv.tuberows.app'
    $TizenTube  = 'io.gh.reisxd.tizentube.cobalt'
    $SourceHint = 'smarttube|teamsmart|liskovsoft'
    $Stats      = @{ Critical = 0; Minor = 0 }
    $ProgressPreference = 'SilentlyContinue'
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    New-Item -ItemType Directory -Path (Split-Path $LogPath) -Force | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $Styles = @{
        OK = @('[OK]  ', 'Green'); ADD = @('[+]   ', 'Cyan'); WARN = @('[!]   ', 'Yellow')
        ASK = @('[?]   ', 'Magenta'); ERR = @('[ERR] ', 'Red'); FATAL = @('[ERR] FATAL: ', 'Red')
    }
    function Write-Status ([string]$Type, [string]$Message) {
        $s = $Styles[$Type]
        Add-Content -Path $LogPath -Value ('{0:yyyy-MM-dd HH:mm:ss} {1}{2}' -f (Get-Date), $s[0], $Message)
        if ($Type -eq 'ERR')   { $Stats.Minor++ }
        if ($Type -eq 'FATAL') { $Stats.Critical++ }
        if (-not $SilentExecution -or $Type -in 'ERR', 'FATAL') {
            Write-Host $s[0] -ForegroundColor $s[1] -NoNewline; Write-Host $Message
        }
    }
    function Write-Detail ([string]$Message, [switch]$Always) {
        Add-Content -Path $LogPath -Value "      $Message"
        if (($VerboseOutput -or $Always) -and -not $SilentExecution) { Write-Host "      $Message" -ForegroundColor DarkGray }
    }
    function Read-Choice ([string]$Prompt, [string]$Default = 'Y') {
        if ($SilentExecution) { return $Default }
        Write-Host '[?]   ' -ForegroundColor Magenta -NoNewline
        $a = Read-Host $Prompt
        if ([string]::IsNullOrWhiteSpace($a)) { $Default } else { $a.Trim() }
    }
    function Invoke-Adb ([string[]]$Arguments) {
        $out = & $script:Adb @Arguments 2>&1 | ForEach-Object { "$_" }
        $text = ($out -join "`n").Trim()
        Write-Detail "adb $($Arguments -join ' ') -> $text"
        [pscustomobject]@{ Code = $LASTEXITCODE; Text = $text }
    }
    function Invoke-TvShell ([string[]]$Arguments) { Invoke-Adb (@('-s', $script:Serial, 'shell') + $Arguments) }

    try {
        Write-Host "TizenTube Recommendations for Projectivy Launcher: installer`n" -ForegroundColor White

        # 1. adb
        $script:Adb = (Get-Command adb.exe -ErrorAction SilentlyContinue | Select-Object -First 1).Source
        $localAdb = Join-Path $env:LOCALAPPDATA 'Android\platform-tools\adb.exe'
        if (-not $script:Adb -and (Test-Path $localAdb)) { $script:Adb = $localAdb }
        if (-not $script:Adb) {
            if ((Read-Choice 'adb not found. Download Google platform-tools (~15 MB) to %LOCALAPPDATA%\Android? (Y/n)') -notmatch '^[Yy]') {
                Write-Status FATAL 'adb is required. Install Android platform-tools and run this again.'; return
            }
            $zip = Join-Path $env:TEMP 'platform-tools-latest-windows.zip'
            Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip -UseBasicParsing
            $dest = Join-Path $env:LOCALAPPDATA 'Android'
            if (Test-Path (Join-Path $dest 'platform-tools')) { Remove-Item (Join-Path $dest 'platform-tools') -Recurse -Force }
            [IO.Compression.ZipFile]::ExtractToDirectory($zip, $dest)
            Remove-Item $zip -Force
            $script:Adb = $localAdb
            Write-Status ADD "platform-tools installed to $dest"
        }
        Write-Status OK "adb: $script:Adb"

        # 2. APK
        if ($ApkPath) {
            if (-not (Test-Path $ApkPath)) { Write-Status FATAL "APK not found: $ApkPath"; return }
            $apk = (Resolve-Path $ApkPath).Path
            Write-Status OK "APK: $apk"
        } else {
            $release = Invoke-RestMethod "https://api.github.com/repos/$Repo/releases/latest" -Headers @{ 'User-Agent' = 'tizentube-recommendations-installer' } -UseBasicParsing
            $asset = $release.assets | Where-Object { $_.name -like '*.apk' } | Select-Object -First 1
            $hash  = $release.assets | Where-Object { $_.name -like '*.apk.sha256' } | Select-Object -First 1
            if (-not $asset) { Write-Status FATAL "No APK in the latest release of $Repo"; return }
            $apk = Join-Path (Split-Path $LogPath) $asset.name
            Invoke-WebRequest $asset.browser_download_url -OutFile $apk -UseBasicParsing
            if ($hash) {
                # GitHub serves .sha256 as binary, so Content may arrive as bytes
                $body = (Invoke-WebRequest $hash.browser_download_url -UseBasicParsing).Content
                if ($body -is [byte[]]) { $body = [Text.Encoding]::ASCII.GetString($body) }
                $expected = ("$body".Trim() -split '\s+')[0].ToUpper()
                $actual = (Get-FileHash $apk -Algorithm SHA256).Hash
                if ($actual -ne $expected) { Write-Status FATAL "SHA-256 mismatch for $($asset.name). Download again."; return }
                Write-Status OK "Downloaded $($release.tag_name) ($($asset.name)), SHA-256 verified"
            } else {
                Write-Status WARN "Downloaded $($release.tag_name) ($($asset.name)); no checksum published to verify"
            }
        }

        # 3. Connect to the TV
        $script:Serial = $null
        if (-not $TvAddress) {
            $online = @((Invoke-Adb @('devices')).Text -split "`n" | Where-Object { $_ -match "`tdevice$" })
            if ($online.Count -gt 1) { Write-Status WARN "Several devices connected; using the first. Set `$TvAddress to choose." }
            if ($online.Count -gt 0) { $script:Serial = ($online[0] -split "`t")[0] }
        }
        if (-not $script:Serial) {
            if (-not $TvAddress) {
                Write-Host '      On the TV: Settings > System > Developer options > Wireless debugging (turn on). Note the IP address and port.' -ForegroundColor DarkGray
                $TvAddress = Read-Choice 'TV IP:port shown under Wireless debugging (or just the IP)' ''
            }
            if (-not $TvAddress) { Write-Status FATAL 'No TV address given'; return }
            if ($TvAddress -notmatch ':\d+$') { $TvAddress = "${TvAddress}:5555" }
            $con = Invoke-Adb @('connect', $TvAddress)
            if ($con.Text -match 'connected to') {
                $script:Serial = $TvAddress
            } else {
                Write-Status WARN "Direct connect to $TvAddress failed; this PC probably needs pairing first"
                if ((Read-Choice 'Pair with the TV now? (Y/n)') -notmatch '^[Yy]') { Write-Status FATAL 'Not connected to the TV'; return }
                Write-Host '      On the TV: Wireless debugging > Pair device with pairing code. Keep that screen open.' -ForegroundColor DarkGray
                $pairAddr = Read-Choice 'Pairing IP:port (shown in the pairing dialog)' ''
                $code     = Read-Choice 'Six-digit pairing code' ''
                if (-not $pairAddr -or -not $code) { Write-Status FATAL 'Pairing needs the pairing IP:port and code'; return }
                $pair = Invoke-Adb @('pair', $pairAddr, $code)
                if ($pair.Text -notmatch 'Successfully paired') { Write-Status FATAL "Pairing failed: $($pair.Text)"; return }
                Write-Status ADD 'Paired with the TV'
                $TvAddress = Read-Choice "Connect IP:port from the main Wireless debugging screen, not the pairing port (Enter = $TvAddress)" $TvAddress
                $con = Invoke-Adb @('connect', $TvAddress)
                if ($con.Text -notmatch 'connected to') { Write-Status FATAL "Connect failed: $($con.Text)"; return }
                $script:Serial = $TvAddress
            }
        }

        $state = ''
        for ($i = 0; $i -lt 15; $i++) {
            $state = (Invoke-Adb @('-s', $script:Serial, 'get-state')).Text
            if ($state -match '^device') { break }
            if ($i -eq 0) { Write-Status ASK 'Waiting for you to allow USB debugging on the TV (tick "Always allow")' }
            Start-Sleep -Seconds 2
        }
        if ($state -notmatch '^device') { Write-Status FATAL "TV did not authorise this PC ($state)"; return }
        $model = (Invoke-TvShell @('getprop', 'ro.product.model')).Text
        Write-Status OK "Connected: $model ($script:Serial)"

        # 4. Prerequisites on the TV
        $packages = @((Invoke-TvShell @('pm', 'list', 'packages')).Text -split "`n" | ForEach-Object { ($_ -replace '^package:', '').Trim() })
        if ($packages -contains $TizenTube) { Write-Status OK 'TizenTube Cobalt installed' }
        else { Write-Status ERR 'TizenTube Cobalt not installed: videos will fall back to SmartTube until you install it' }
        $smartTube = @($packages | Where-Object { $_ -match $SourceHint })
        if ($smartTube) { Write-Status OK "SmartTube installed: $($smartTube -join ', ')" }
        else { Write-Status ERR 'SmartTube not installed: the app has nothing to mirror until you install and sign in to it' }

        # 5. Install and grant
        $inst = Invoke-Adb @('-s', $script:Serial, 'install', '-r', '-g', $apk)
        if ($inst.Text -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
            Write-Status WARN 'An existing copy is signed with a different key'
            if ((Read-Choice 'Uninstall it and install this one? Its settings will reset (Y/n)') -match '^[Yy]') {
                $null = Invoke-Adb @('-s', $script:Serial, 'uninstall', $AppId)
                $inst = Invoke-Adb @('-s', $script:Serial, 'install', '-r', '-g', $apk)
            }
        }
        if ($inst.Text -notmatch 'Success') { Write-Status FATAL "Install failed: $($inst.Text)"; return }
        Write-Status ADD 'App installed'

        $grant = Invoke-TvShell @('pm', 'grant', $AppId, 'android.permission.READ_TV_LISTINGS')
        if ($grant.Code -eq 0) { Write-Status ADD 'READ_TV_LISTINGS granted' } else { Write-Status ERR "Permission grant failed: $($grant.Text)" }

        # 6. Battery exemption: keeps background refreshes running for apps you never open (SmartTube)
        if ($BatteryExempt) {
            foreach ($pkg in @($AppId) + $smartTube) {
                $null = Invoke-TvShell @('dumpsys', 'deviceidle', 'whitelist', "+$pkg")
            }
            $whitelist = (Invoke-TvShell @('dumpsys', 'deviceidle', 'whitelist')).Text
            foreach ($pkg in @($AppId) + $smartTube) {
                if ($whitelist -match [regex]::Escape($pkg)) { Write-Status ADD "Battery-exempt: $pkg" }
                else { Write-Status ERR "Battery exemption not applied: $pkg" }
            }
        } else { Write-Status WARN 'Battery exemption skipped: SmartTube refreshes may slow to once a day' }

        # 7. First run: opening the app asks SmartTube to refresh, then mirrors its rows
        $null = Invoke-Adb @('-s', $script:Serial, 'logcat', '-c')
        $null = Invoke-TvShell @('am', 'start', '-n', "$AppId/.MainActivity")
        Write-Status ASK 'Waiting 60 seconds for the first sync'
        Start-Sleep -Seconds 60
        $log = @((Invoke-Adb @('-s', $script:Serial, 'logcat', '-d', '-s', 'TubeRows:I')).Text -split "`n" |
            Where-Object { $_ -match 'TubeRows' } | ForEach-Object { $_ -replace '^.*?TubeRows\s*:\s*', '' })
        $log | Select-Object -Last 8 | ForEach-Object { Write-Detail $_ -Always }

        $visibility = $log | Where-Object { $_ -match 'Visible channel packages' } | Select-Object -Last 1
        # The line always contains the words "SmartTube rows=", so test for a non-empty list, not the hint.
        if ($visibility -match 'SmartTube rows=\[[^\]]') { Write-Status OK 'SmartTube rows found and mirrored' }
        elseif ($visibility) { Write-Status ERR 'SmartTube rows not visible yet: open SmartTube once (signed in), then open "TizenTube Rows" on the TV' }
        else { Write-Status ERR 'No sync logged yet: open "TizenTube Rows" on the TV and check its status screen' }
        $null = Invoke-TvShell @('input', 'keyevent', 'KEYCODE_HOME')

        Write-Host "`nNext, in Projectivy Launcher: Settings > Channels, enable the TizenTube Rows channels (and hide SmartTube's)." -ForegroundColor White
        Write-Host 'Tip: turn Wireless debugging off on the TV when you are done.' -ForegroundColor DarkGray
    }
    catch {
        Write-Status FATAL "$($_.Exception.Message)"
    }
    finally {
        $c = $Stats.Critical; $m = $Stats.Minor
        $line = "Install completed with $c critical error$(if ($c -ne 1) {'s'}), $m minor error$(if ($m -ne 1) {'s'}). Log: $LogPath"
        Add-Content -Path $LogPath -Value $line
        Write-Host "`n$line" -ForegroundColor $(if ($c) { 'Red' } elseif ($m) { 'Yellow' } else { 'Green' })
        # "Run with PowerShell" closes the window when the script ends; keep the result on screen.
        if (-not $SilentExecution) { $null = Read-Host 'Press Enter to close' }
    }
}
