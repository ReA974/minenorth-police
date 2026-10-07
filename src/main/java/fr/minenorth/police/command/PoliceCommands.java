package fr.minenorth.police.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.minenorth.police.JailService;
import fr.minenorth.police.PoliceService;
import fr.minenorth.police.config.PoliceConfig;
import fr.minenorth.police.data.PoliceData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** Commandes OP : /police grade <joueur> <commissaire|officier|sousofficier|aucun>, /police tablette <joueur>,
 *  /police equipement <joueur> (radar, détecteur, taser, cartouches, tests), /police reload,
 *  /police cellule ajouter|supprimer <nom>, /police cellule liste|sortie, /police liberer <joueur>. */
@Mod.EventBusSubscriber
public final class PoliceCommands {
    private PoliceCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var grade = Commands.literal("grade").then(Commands.argument("joueur", EntityArgument.player())
                .then(Commands.literal("commissaire").executes(c -> setGrade(c.getSource(), EntityArgument.getPlayer(c, "joueur"), PoliceData.COMMISSAIRE)))
                .then(Commands.literal("officier").executes(c -> setGrade(c.getSource(), EntityArgument.getPlayer(c, "joueur"), PoliceData.OFFICIER)))
                .then(Commands.literal("sousofficier").executes(c -> setGrade(c.getSource(), EntityArgument.getPlayer(c, "joueur"), PoliceData.SOUS_OFFICIER)))
                .then(Commands.literal("aucun").executes(c -> setGrade(c.getSource(), EntityArgument.getPlayer(c, "joueur"), -1))));
        event.getDispatcher().register(Commands.literal("police").requires(s -> s.hasPermission(2))
                .then(grade)
                .then(Commands.literal("tablette").then(Commands.argument("joueur", EntityArgument.player()).executes(c -> {
                    ServerPlayer target = EntityArgument.getPlayer(c, "joueur");
                    PoliceService.giveTablet(target);
                    c.getSource().sendSystemMessage(Component.literal("§aTablette de police remise à " + fr.minenorth.api.MineNorth.displayName(target) + "."));
                    return 1;
                })))
                .then(Commands.literal("equipement").then(Commands.argument("joueur", EntityArgument.player()).executes(c -> {
                    ServerPlayer target = EntityArgument.getPlayer(c, "joueur");
                    int n = PoliceService.giveKit(target);
                    c.getSource().sendSystemMessage(Component.literal("§aÉquipement de police remis à " + fr.minenorth.api.MineNorth.displayName(target) + " (" + n + " objet(s))."));
                    return 1;
                })))
                .then(cellule())
                .then(Commands.literal("liberer").then(Commands.argument("joueur", GameProfileArgument.gameProfile()).executes(c -> {
                    int n = 0;
                    for (GameProfile gp : GameProfileArgument.getGameProfiles(c, "joueur")) {
                        JailService.Result r = JailService.release(c.getSource().getServer(), gp.getId(), "libéré par un administrateur");
                        c.getSource().sendSystemMessage(Component.literal((r.ok() ? "§a" : "§c") + gp.getName() + " : " + r.msg()));
                        if (r.ok()) n++;
                    }
                    return n;
                })))
                .then(Commands.literal("reload").executes(c -> {
                    boolean ok = PoliceConfig.load();
                    c.getSource().sendSystemMessage(Component.literal(ok ? "§aConfiguration de la police rechargée."
                            : "§cFichier minenorth_police.json illisible : ancienne configuration conservée."));
                    return ok ? 1 : 0;
                })));
    }

    /** /police cellule ajouter|supprimer <nom>, liste, sortie : placement des cellules de la prison. */
    private static LiteralArgumentBuilder<CommandSourceStack> cellule() {
        return Commands.literal("cellule")
                .then(Commands.literal("ajouter").then(Commands.argument("nom", StringArgumentType.word()).executes(c -> {
                    String msg = JailService.addCell(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "nom"));
                    c.getSource().sendSystemMessage(Component.literal(msg));
                    return 1;
                })))
                .then(Commands.literal("supprimer").then(Commands.argument("nom", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                PoliceData.get(c.getSource().getServer()).cells.stream().map(cell -> cell.name), b))
                        .executes(c -> {
                            c.getSource().sendSystemMessage(Component.literal(JailService.removeCell(c.getSource().getServer(), StringArgumentType.getString(c, "nom"))));
                            return 1;
                        })))
                .then(Commands.literal("liste").executes(c -> {
                    List<String> lines = JailService.listCells(c.getSource().getServer());
                    c.getSource().sendSystemMessage(Component.literal("§e" + (lines.size() - 1) + " cellule(s) :"));
                    for (String line : lines) c.getSource().sendSystemMessage(Component.literal(line));
                    return 1;
                }))
                .then(Commands.literal("sortie").executes(c -> {
                    c.getSource().sendSystemMessage(Component.literal(JailService.setExit(c.getSource().getPlayerOrException())));
                    return 1;
                }));
    }

    private static int setGrade(net.minecraft.commands.CommandSourceStack source, ServerPlayer target, int grade) {
        String result = PoliceService.setGrade(target.server, target.getUUID(), target.getGameProfile().getName(), grade);
        source.sendSystemMessage(Component.literal("§a" + result));
        return 1;
    }
}
