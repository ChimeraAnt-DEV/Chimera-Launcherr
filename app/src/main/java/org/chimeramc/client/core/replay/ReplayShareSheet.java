package org.chimeramc.client.core.replay;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.chimeramc.client.R;

import java.io.File;

/**
 * Opens the system share sheet for a clip.
 *
 * <p>The gallery export copies a clip into {@code Movies/GlowberryClient}, but getting it to
 * Discord or a chat app from there is several steps. This shares the clip file directly with
 * {@code ACTION_SEND} and a {@code FileProvider} content uri, which is what an app on API 24+
 * needs to hand a private file to another app.
 *
 * <p>Deliberately best-effort: with no app that can receive a video the chooser still opens (the
 * user is told), and a failure to build the uri is reported rather than crashing the overlay.
 */
public final class ReplayShareSheet {

    private ReplayShareSheet() {
    }

    /**
     * Shares {@code clip} through the system chooser.
     *
     * @return true when the chooser was launched, false when there was nothing shareable or the
     *         uri could not be built.
     */
    public static boolean share(Context context, ReplayClip clip) {
        if (context == null || clip == null) return false;
        File file = clip.file();
        if (!ReplaySharing.isShareable(file)) {
            Toast.makeText(context, R.string.replay_share_failed, Toast.LENGTH_SHORT).show();
            return false;
        }

        Uri uri;
        try {
            uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", file);
        } catch (Throwable t) {
            Toast.makeText(context, R.string.replay_share_failed, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (uri == null) {
            Toast.makeText(context, R.string.replay_share_failed, Toast.LENGTH_SHORT).show();
            return false;
        }

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(ReplaySharing.mimeType(file));
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_SUBJECT, ReplaySharing.shareTitle(clip));
        send.putExtra(Intent.EXTRA_TITLE, ReplaySharing.shareTitle(clip));
        // Without the grant the receiving app cannot read the content uri and the share silently
        // fails with a security exception on its side.
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            Intent chooser = Intent.createChooser(send,
                    context.getString(R.string.replay_action_share));
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
            return true;
        } catch (Throwable t) {
            Toast.makeText(context, R.string.replay_share_no_app, Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
