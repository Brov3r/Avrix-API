package com.avrix.api.commands;

import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.COverride;
import zombie.characters.Role;
import zombie.characters.Roles;
import zombie.commands.CommandBase;
import zombie.core.raknet.UdpConnection;
import zombie.debug.DebugType;
import zombie.debug.LogSeverity;
import zombie.network.GameServer;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;

/**
 * ClassTransform mixin prioritizing Avrix custom commands over vanilla Project Zomboid commands.
 *
 * @author Avrix Engine Team
 */
@CTransformer(value = GameServer.class)
public class GameServerCommandsMixin {

    /**
     * Overwrites GameServer#handleServerCommand(String, UdpConnection) to first check
     * the Avrix {@link CommandManager}, and gracefully fall back to native PZ {@link CommandBase}.
     */
    @COverride
    public static String handleServerCommand(String input, UdpConnection connection) {
        if (input == null) {
            return null;
        }

        String avrixResult = CommandManager.handleCommand(input, connection);
        if (avrixResult != null) {
            return avrixResult;
        }

        String adminUsername = "admin";
        Role accessLevel = Roles.getDefaultForAdmin();

        if (connection != null) {
            adminUsername = connection.getUserName();
            if (!connection.isCoopHost) {
                accessLevel = connection.getRole();
            }
        }

        Class<?> cls = CommandBase.findCommandCls(input);
        if (cls != null) {
            Constructor<?> constructor = cls.getConstructors()[0];
            try {
                CommandBase command = (CommandBase) constructor.newInstance(adminUsername, accessLevel, input, connection);
                return command.Execute();
            } catch (IllegalAccessException e) {
                DebugType.General.printException(e, "", LogSeverity.Error);
                return "A IllegalAccessException error occured";
            } catch (InstantiationException e2) {
                DebugType.General.printException(e2, "", LogSeverity.Error);
                return "A InstantiationException error occured";
            } catch (InvocationTargetException e3) {
                DebugType.General.printException(e3, "", LogSeverity.Error);
                return "A InvocationTargetException error occured";
            } catch (SQLException e4) {
                DebugType.General.printException(e4, "", LogSeverity.Error);
                return "A SQL error occured";
            }
        }

        return "Unknown command " + input;
    }
}