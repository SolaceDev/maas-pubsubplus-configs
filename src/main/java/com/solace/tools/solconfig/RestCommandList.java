package com.solace.tools.solconfig;

import com.solace.tools.solconfig.model.HTTPMethod;
import com.solace.tools.solconfig.model.SEMPError;
import com.solace.tools.solconfig.model.SolConfigException;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

@Slf4j
public class RestCommandList {

    class Command {
        private HTTPMethod method;
        private String resourcePath;
        private String payload;

        Command(HTTPMethod method, String resourcePath, String payload) {
            this.method = method;
            this.resourcePath = resourcePath;
            this.payload = payload;
        }

        @Override
        public String toString() {
            return String.format("%s %s%s%n", method.name(), resourcePath,
                    Objects.isNull(payload) || payload.isEmpty() ? "" : "\n" + payload);
        }
    }

    private List<Command> commands = new LinkedList<>();

    public void append(HTTPMethod method, String resourcePath, String payload) {
        commands.add(new Command(method, resourcePath, payload));
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        commands.forEach(sb::append);
        return sb.toString();
    }

    public void execute(SempClient sempClient, boolean curlOnly) {
        if (curlOnly) {
            log.info(toCurlScript(sempClient));
        } else {
            execute(sempClient, this.commands);
        }
    }

    private void execute(SempClient sempClient, List<Command> commandList) {
        List<Command> retryCommands = new LinkedList<>();
        for (Command cmd : commandList) {
            Utils.log(String.format("%s %s ", cmd.method.name(), cmd.resourcePath));
            var meta = sempClient.sendAndGetMeta(cmd.method.name(), cmd.resourcePath, cmd.payload);
            if (meta.getResponseCode() == 200) {
                Utils.log("OK");
            } else {
                int semp_code = meta.getError().getCode();
                if (cmd.method == HTTPMethod.DELETE &&
                        (semp_code == SEMPError.NOT_ALLOWED.getValue() ||
                                semp_code == SEMPError.CONFIGDB_OBJECT_DEPENDENCY.getValue())) {
                    Utils.err("%s, retry later%n", SEMPError.ofInt(semp_code));
                    retryCommands.add(cmd);
                } else if (cmd.method == HTTPMethod.POST && // add new object
                        semp_code == SEMPError.NOT_FOUND.getValue()) { // means that the new object depends on some
                                                                       // objects that does not exist
                    Utils.err("%s, retry later%n", SEMPError.ofInt(semp_code));
                    retryCommands.add(cmd);
                } else if (cmd.method == HTTPMethod.POST &&
                        semp_code == SEMPError.ALREADY_EXISTS.getValue()) {
                    Utils.err("%s%n", SEMPError.ALREADY_EXISTS);
                } else {
                    Utils.err("%n%s%s%n", Objects.nonNull(cmd.payload) ? cmd.payload + "\n" : "", meta.toString());
                    if (!Utils.isExitOnErrors()) {
                        throw new SolConfigException("Error executing command list: " +
                                String.format("%n%s%s%n", Objects.nonNull(cmd.payload) ? cmd.payload + "\n" : "", meta));
                    }
                    System.exit(1);
                }
            }
        }
        if (!retryCommands.isEmpty())
            execute(sempClient, retryCommands);
    }

    private String toCurlScript(SempClient sempClient) {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/sh +x\n");
        sb.append(String.format("export SEMP_HOST=%s%n", sempClient.getBaseUrl()));
        sb.append(String.format("export SEMP_ADMIN=%s%n", sempClient.getAdminUser()));
        sb.append(String.format("export SEMP_PWD=%s%n", sempClient.getAdminPwd()));

        for (Command cmd : commands) {
            sb.append("\n");
            String uri = sempClient.uriAddOpaquePassword(cmd.resourcePath);
            sb.append(String.format("curl -X %s -u $SEMP_ADMIN:$SEMP_PWD \"$SEMP_HOST%s\"",
                    cmd.method.name(), uri));
            if (Objects.nonNull(cmd.payload) && cmd.payload.length() > 0) {
                sb.append(" -H 'content-type: application/json' -d '");
                sb.append(String.format("%s'%n", cmd.payload));
            }
        }
        return sb.toString();
    }

    public RestCommandList addAll(RestCommandList toAdd) {
        commands.addAll(toAdd.commands);
        return this;
    }

    public int size() {
        return commands.size();
    }
}