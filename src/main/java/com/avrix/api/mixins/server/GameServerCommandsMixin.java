package com.avrix.api.mixins.server;

import com.avrix.api.commands.CommandManager;
import com.avrix.api.commands.CommandMessages;
import com.avrix.api.commands.CommandSource;
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
 */
@CTransformer(value = GameServer.class)
public class GameServerCommandsMixin {

    /**
     * Prevents instantiation of the transformer class.
     */
    private GameServerCommandsMixin() {
        throw new UnsupportedOperationException("Mixin classes cannot be instantiated");
    }

    /**
     * Overwrites GameServer#handleServerCommand(String, UdpConnection) to first check
     * the Avrix {@link CommandManager}, and gracefully fall back to native PZ {@link CommandBase}.
     *
     * @param input      raw command input
     * @param connection issuing server connection
     * @return command result, or {@code null} when PZ should handle the command
     */
    @COverride
    public static String handleServerCommand(String input, UdpConnection connection) {
        if (input == null) {
            return null;
        }

        String avrixResult = CommandManager.dispatchCommand(input, CommandSource.of(connection));
        if (avrixResult != null) {
            return avrixResult;
        }

        String adminUsername = "admin";
        Role accessLevel = Roles.getDefaultForAdmin();

        if (connection != null) {
            adminUsername = connection.getUserName();
            if (!connection.isCoopHost) {
                accessLevel = connection.getRole();
                if (accessLevel == null) {
                    accessLevel = Roles.getDefaultForUser();
                }
            }
        }

        try {
            Class<?> cls = CommandBase.findCommandCls(input);
            if (cls == null) {
                return "Unknown command " + input;
            }

            Constructor<?> constructor = cls.getDeclaredConstructor(
                    String.class, Role.class, String.class, UdpConnection.class
            );
            constructor.setAccessible(true);
            CommandBase command = (CommandBase) constructor.newInstance(
                    adminUsername, accessLevel, input, connection
            );
            return command.Execute();
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException
                 | InvocationTargetException | SQLException | RuntimeException ex) {
            DebugType.General.printException(ex, "", LogSeverity.Error);
            return CommandMessages.internalError("vanilla");
        }
    }
}