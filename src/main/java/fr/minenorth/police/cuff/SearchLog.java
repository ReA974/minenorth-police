package fr.minenorth.police.cuff;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Journal des fouilles (qui a fouillé qui, quoi a été saisi) : config/minenorth_police_fouilles.log, une ligne par évènement, écrit côté serveur. */
public final class SearchLog {
    private SearchLog() {}

    public static void write(MinecraftServer s, ServerPlayer by, ServerPlayer target, String what) {
        try {
            Path f = FMLPaths.CONFIGDIR.get().resolve("minenorth_police_fouilles.log");
            String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " | " + by.getGameProfile().getName() + " -> "
                    + target.getGameProfile().getName() + " | " + what + System.lineSeparator();
            Files.writeString(f, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
