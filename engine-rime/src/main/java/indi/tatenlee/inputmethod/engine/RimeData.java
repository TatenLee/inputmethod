package indi.tatenlee.inputmethod.engine;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** 共享词库可随版本更新，用户学习词库留在独立目录，永不覆盖。 */
final class RimeData {
    static String[] install(Context context) throws IOException {
        File shared = new File(context.getFilesDir(), "rime/shared");
        File user = new File(context.getFilesDir(), "rime/user");
        if (!shared.isDirectory() && !shared.mkdirs()) throw new IOException("shared directory");
        if (!user.isDirectory() && !user.mkdirs()) throw new IOException("user directory");
        String revision;
        try (var input = context.getAssets().open("rime/revision.txt")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            revision = bytes.toString(StandardCharsets.UTF_8.name());
        }
        File marker = new File(shared, ".revision");
        if (!marker.exists() || !new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).equals(revision)) {
            copyDirectory(context.getAssets(), "rime", shared);
            Files.write(marker.toPath(), revision.getBytes(StandardCharsets.UTF_8));
        }
        return new String[] {shared.getAbsolutePath(), user.getAbsolutePath()};
    }

    private static void copyDirectory(AssetManager assets, String source, File target) throws IOException {
        String[] names = assets.list(source);
        if (names == null) throw new IOException("Missing asset " + source);
        for (String name : names) {
            String path = source + "/" + name;
            File output = new File(target, name);
            String[] children = assets.list(path);
            if (children != null && children.length > 0) {
                if (!output.isDirectory() && !output.mkdirs()) throw new IOException("Directory " + name);
                copyDirectory(assets, path, output);
            } else {
                File temporary = new File(target, name + ".installing");
                try (var input = assets.open(path); var stream = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[65536]; int count;
                    while ((count = input.read(buffer)) != -1) stream.write(buffer, 0, count);
                }
                if (!temporary.renameTo(output)) throw new IOException("Install " + name);
            }
        }
    }
}
