package fr.minenorth.police.command;

import fr.minenorth.police.PoliceService;
import fr.minenorth.police.config.PoliceConfig;
import fr.minenorth.police.data.PoliceData;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Commandes OP : /police grade <joueur> <commissaire|officier|sousofficier|aucun>, /police tablette <joueur>,
 *  /police equipement <joueur> (radar, détecteur, taser, cartouches, tests), /police reload. */
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
                .then(Commands.literal("reload").executes(c -> {
                    boolean ok = PoliceConfig.load();
                    c.getSource().sendSystemMessage(Component.literal(ok ? "§aConfiguration de la police rechargée."
                            : "§cFichier minenorth_police.json illisible : ancienne configuration conservée."));
                    return ok ? 1 : 0;
                })));
    }

    private static int setGrade(net.minecraft.commands.CommandSourceStack source, ServerPlayer target, int grade) {
        String result = PoliceService.setGrade(target.server, target.getUUID(), target.getGameProfile().getName(), grade);
        source.sendSystemMessage(Component.literal("§a" + result));
        return 1;
    }
}
