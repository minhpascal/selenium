// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.openqa.grid.selenium;

import org.openqa.grid.common.CommandLineOptionHelper;
import org.openqa.grid.common.GridDocHelper;
import org.openqa.grid.common.GridRole;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.common.exception.GridConfigurationException;
import org.openqa.grid.internal.utils.GridHubConfiguration;
import org.openqa.grid.internal.utils.SelfRegisteringRemote;
import org.openqa.grid.web.Hub;
import org.openqa.selenium.remote.server.log.LoggingOptions;
import org.openqa.selenium.remote.server.log.TerseFormatter;
import org.openqa.selenium.server.SeleniumServer;
import org.openqa.selenium.server.cli.RemoteControlLauncher;

import java.io.File;
import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GridLauncher {

  private static final Logger log = Logger.getLogger(GridLauncher.class.getName());

  public static void main(String[] args) throws Exception {

    CommandLineOptionHelper helper = new CommandLineOptionHelper(args);
    GridRole role = GridRole.find(args);

    if (role == null) {
      System.out.println(
        "\n" +
        "The role '" + helper.getParamValue("-role") + "' does not match a recognized role for Selenium server\n" +
        "\n" +
        "Selenium server can run in one of the following roles:\n" +
        "    hub         as a hub of a Selenium grid\n" +
        "    node        as a node of a Selenium grid\n" +
        "    standalone  as a standalone server not being a part of a grid\n" +
        "\n" +
        "If -role option is not specified the server runs standalone\n" +
        "\n");
      RemoteControlLauncher.printWrappedLine("",
        "To get help on the command line options available for each role run the server with both -role and -help options");
      return;
    }

    if (helper.isParamPresent("-help") || helper.isParamPresent("-h")){
      String separator = "\n-----------------------------------------\n";
      if (role == GridRole.NOT_GRID) {
        RemoteControlLauncher.usage(separator+"To use as a standalone server"+separator);
      } else if (role == GridRole.HUB) {
        GridDocHelper.printHubHelp(
          separator + "To use in a grid environment as the hub:" + separator, false);
      } else if (role == GridRole.NODE) {
        GridDocHelper.printNodeHelp(separator + "To use in a grid environment as a node:" + separator, false);
      } else {
        GridDocHelper.printHubHelp(separator + "To use in a grid environment :" + separator, false);
      }
      return;
    }

    configureLogging(helper);

    switch (role) {
      case NOT_GRID:
        log.info("Launching a standalone Selenium Server");
        SeleniumServer.main(args);
        log.info("Selenium Server is up and running");
        break;
      case HUB:
        log.info("Launching Selenium Grid hub");
        try {
          GridHubConfiguration c = GridHubConfiguration.build(args);
          Hub h = new Hub(c);
          h.start();
          log.info("Nodes should register to " + h.getRegistrationURL());
          log.info("Selenium Grid hub is up and running");
        } catch (GridConfigurationException e) {
          GridDocHelper.printHubHelp(e.getMessage());
          e.printStackTrace();
        }
        break;
      case NODE:
        log.info("Launching a Selenium Grid node");
        try {
          RegistrationRequest c = RegistrationRequest.build(args);
          SelfRegisteringRemote remote = new SelfRegisteringRemote(c);
          remote.startRemoteServer();
          log.info("Selenium Grid node is up and ready to register to the hub");
          remote.startRegistrationProcess();
        } catch (GridConfigurationException e) {
          GridDocHelper.printNodeHelp(e.getMessage());
          e.printStackTrace();
        }
        break;
      default:
        throw new RuntimeException("NI");
    }
  }

  private static void configureLogging(CommandLineOptionHelper helper) {
    Level logLevel =
        helper.isParamPresent("-debug")
        ? Level.FINE
        : LoggingOptions.getDefaultLogLevel();
    if (logLevel == null) {
      logLevel = Level.INFO;
    }
    Logger.getLogger("").setLevel(logLevel);
    Logger.getLogger("org.openqa.jetty").setLevel(Level.WARNING);

    String logFilename =
        helper.isParamPresent("-log")
        ? helper.getParamValue("-log")
        : LoggingOptions.getDefaultLogOutFile();
    if (logFilename != null) {
      for (Handler handler : Logger.getLogger("").getHandlers()) {
        if (handler instanceof ConsoleHandler) {
          Logger.getLogger("").removeHandler(handler);
        }
      }
      try {
        Handler logFile = new FileHandler(new File(logFilename).getAbsolutePath(), true);
        logFile.setFormatter(new TerseFormatter(true));
        logFile.setLevel(logLevel);
        Logger.getLogger("").addHandler(logFile);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } else {
      boolean logLongForm = helper.isParamPresent("-logLongForm");
      for (Handler handler : Logger.getLogger("").getHandlers()) {
        if (handler instanceof ConsoleHandler) {
          handler.setLevel(logLevel);
          handler.setFormatter(new TerseFormatter(logLongForm));
        }
      }
    }
  }
}
