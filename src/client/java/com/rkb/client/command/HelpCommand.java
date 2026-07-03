package com.rkb.client.command;

public class HelpCommand extends Command {

    public HelpCommand() {
        super("help");
    }

    @Override
    public void execute(String[] args) {
        System.out.println("=== RKB Client Commands ===");
        System.out.println(".help");
        System.out.println(".toggle <module>");
        System.out.println(".bind <module> <key>");
        System.out.println(".config save");
        System.out.println(".config load");
    }
}
