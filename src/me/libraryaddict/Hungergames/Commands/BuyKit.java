package me.libraryaddict.Hungergames.Commands;

import me.libraryaddict.Hungergames.Managers.KitManager;
import me.libraryaddict.Hungergames.Managers.PlayerManager;
import me.libraryaddict.Hungergames.Managers.TranslationManager;
import me.libraryaddict.Hungergames.Types.Gamer;
import me.libraryaddict.Hungergames.Types.GiveKitThread;
import me.libraryaddict.Hungergames.Types.HungergamesApi;

import org.apache.commons.lang.StringUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class BuyKit implements CommandExecutor {
    private TranslationManager cm = HungergamesApi.getTranslationManager();
    public String description = "When mysql is enabled you can use this command to buy kits";
    private KitManager kits = HungergamesApi.getKitManager();
    private PlayerManager pm = HungergamesApi.getPlayerManager();

    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        Gamer gamer = pm.getGamer(sender.getName());
        if (args.length > 0) {
            me.libraryaddict.Hungergames.Types.Kit kit = kits.getKitByName(StringUtils.join(args, " "));
            if (kit != null) {
                if (gamer.getBalance() < kit.getPrice()) {
                    sender.sendMessage(cm.getCommandBuyKitCantAfford());
                    return true;
                }
                if (kit.getPrice() == -1 || kit.isFree()) {
                    sender.sendMessage(cm.getCommandBuyKitCantBuyKit());
                    return true;
                }
                if (kits.ownsKit(gamer.getPlayer(), kit)) {
                    sender.sendMessage(cm.getCommandBuyKitAlreadyOwn());
                    return true;
                }
                if (!HungergamesApi.getConfigManager().isMySqlEnabled()) {
                    sender.sendMessage(cm.getCommandBuyKitMysqlNotEnabled());
                    return true;
                }
                if (!kits.addKitToPlayer(gamer.getPlayer(), kit)) {
                    sender.sendMessage(cm.getCommandBuyKitKitsNotLoaded());
                } else {
                    gamer.addBalance(-kit.getPrice());
                    new GiveKitThread(gamer.getName(), kit.getName()).start();
                    sender.sendMessage(cm.getCommandBuyKitPurchasedKit());
                }
                return true;
            }
        }
        sender.sendMessage(cm.getCommandBuyKitNoArgs());
        return true;
    }
}
