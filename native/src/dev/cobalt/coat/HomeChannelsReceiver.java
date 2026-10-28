// Copyright 2026 The Cobalt Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cobalt.coat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;

/**
 * Hears the home screen tell us the user removed one of our cards, so {@link HomeChannels} never
 * puts it back (Watch Next cards return only after the video is watched again).
 *
 * <p>Exported because the system sends these broadcasts. Another app could only fake a removal of
 * one of our own cards, which hides it; nothing else is reachable through here.
 */
public final class HomeChannelsReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent == null) return;
    long id = intent.getLongExtra(TvContract.EXTRA_PREVIEW_PROGRAM_ID, -1);
    if (TvContract.ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED.equals(intent.getAction()) && id >= 0) {
      HomeChannels.onRemoved(context, id, false, goAsync());
      return;
    }
    id = intent.getLongExtra(TvContract.EXTRA_WATCH_NEXT_PROGRAM_ID, -1);
    if (TvContract.ACTION_WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED.equals(intent.getAction()) && id >= 0) {
      HomeChannels.onRemoved(context, id, true, goAsync());
    }
  }
}
