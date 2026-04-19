package com.ahead.aiagent.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerminalOperationToolTest {

    @Test
    void executeTerminalCommand() {

        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        String result = terminalOperationTool.executeTerminalCommand("dir");
       Assertions.assertNotNull(result);
    }
}