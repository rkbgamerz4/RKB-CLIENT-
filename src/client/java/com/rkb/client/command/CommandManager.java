package com.rkb.client.command;

import java.util.ArrayList;
import java.util.List;

public class CommandManager {

    private final List<Command> commands = new ArrayList<>();

    public void register(Command command) {
        commands.add(command);
    }

    public List<Command> getCommands() {
        return commands;
    }

    public void execute(String input) {
        if (!input.startsWith(".")) return;

        String[] split = input.substring(1).split(" ");
        String name = split[0];

        for (Command command : commands) {
            if (command.getName().equalsIgnoreCase(name)) {
                command.execute(split);
                return;
            }
        }

        System.out.println("Unknown command: " + name);
    }
}
