/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.notification;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.notification.impl.NotificationSettings;
import com.intellij.notification.impl.NotificationsConfigurable;
import com.intellij.notification.impl.NotificationsConfiguration;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * @author peter
 */
public class EventLog implements Notifications {
  private final List<Notification> myNotifications = new ArrayList<Notification>();

  public EventLog() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(Notifications.TOPIC, this);
  }

  @Override
  public void notify(@NotNull Notification notification) {
    logNotification(null, notification);
  }

  private void addGlobalNotifications(Notification notification) {
    synchronized (myNotifications) {
      myNotifications.add(notification);
    }
  }

  public List<Notification> takeNotifications() {
    synchronized (myNotifications) {
      final ArrayList<Notification> result = new ArrayList<Notification>(myNotifications);
      myNotifications.clear();
      return result;
    }
  }

  private static void logNotification(@Nullable final Project project, final Notification notification) {
    final NotificationSettings settings = NotificationsConfiguration.getSettings(notification.getGroupId());
    if (!settings.isShouldLog()) {
      return;
    }

    if (project == null) {
      final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
      if (openProjects.length == 0) {
        getApplicationComponent().addGlobalNotifications(notification);
      }
      for (Project p : openProjects) {
        printNotification(getProjectComponent(p).myConsoleView, p, notification);
      }
    } else {
      printNotification(getProjectComponent(project).myConsoleView, project, notification);
    }
  }

  private static void printNotification(ConsoleViewImpl view, Project project, final Notification notification) {
    view.print(DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date()) + " ", ConsoleViewContentType.NORMAL_OUTPUT);

    boolean showLink = notification.getListener() != null;
    String content = notification.getContent();
    String mainText = notification.getTitle();
    if (StringUtil.isNotEmpty(content) && !content.startsWith("<")) {
      if (StringUtil.isNotEmpty(mainText)) {
        mainText += ": ";
      }
      mainText += content;
    }

    int nlIndex = eolIndex(mainText);
    if (nlIndex >= 0) {
      mainText = mainText.substring(0, nlIndex);
      showLink = true;
    }

    mainText = mainText.replaceAll("<[^>]*>", "");

    final NotificationType type = notification.getType();
    view.print(mainText, type == NotificationType.ERROR
                         ? ConsoleViewContentType.ERROR_OUTPUT
                         : type == NotificationType.INFORMATION
                           ? ConsoleViewContentType.NORMAL_OUTPUT
                           : ConsoleViewContentType.WARNING_OUTPUT);
    if (showLink) {
      view.print(" ", ConsoleViewContentType.NORMAL_OUTPUT);
      view.printHyperlink("more", new HyperlinkInfo() {
        @Override
        public void navigate(Project project) {
          Balloon balloon = notification.getBalloon();
          if (balloon != null) {
            balloon.hide();
          }

          NotificationsManagerImpl.notifyByBalloon(notification, NotificationDisplayType.STICKY_BALLOON, project);
        }
      });
      view.print(" ", ConsoleViewContentType.NORMAL_OUTPUT);
    }
    view.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);

    notifyStatusBar(project, mainText);
  }

  private static void notifyStatusBar(Project project, @Nullable String mainText) {
    final IdeFrame frame = WindowManager.getInstance().getIdeFrame(project);
    if (frame != null) {
      final StatusBar statusBar = frame.getStatusBar();
      if (statusBar != null) {
        ((StatusBarEx)statusBar).setLogMessage(mainText);
      }
    }
  }

  private static int eolIndex(String mainText) {
    int nlIndex = mainText.indexOf("<br>");
    if (nlIndex < 0) nlIndex = mainText.indexOf("<br/>");
    if (nlIndex < 0) nlIndex = mainText.indexOf("\n");
    return nlIndex;
  }

  private static EventLog getApplicationComponent() {
    return ApplicationManager.getApplication().getComponent(EventLog.class);
  }

  @Override
  public void register(@NotNull String groupDisplayType, @NotNull NotificationDisplayType defaultDisplayType) {
  }

  public static class ProjectTracker extends AbstractProjectComponent {
    private final ConsoleViewImpl myConsoleView;
    private final List<Notification> myGlobalNotifications;

    public ProjectTracker(final Project project) {
      super(project);
      myConsoleView = new ConsoleViewImpl(project, true);
      myGlobalNotifications = getApplicationComponent().takeNotifications();
      project.getMessageBus().connect(project).subscribe(Notifications.TOPIC, new Notifications() {
        @Override
        public void notify(@NotNull Notification notification) {
          projectOpened();
          logNotification(project, notification);
        }

        @Override
        public void register(@NotNull String groupDisplayType, @NotNull NotificationDisplayType defaultDisplayType) {
        }
      });
    }

    @Override
    public void projectOpened() {
      for (Notification globalNotification : myGlobalNotifications) {
        logNotification(myProject, globalNotification);
      }
      myGlobalNotifications.clear();
    }

    @Override
    public void projectClosed() {
      notifyStatusBar(myProject, null);
    }
  }

  public static ProjectTracker getProjectComponent(Project project) {
    return project.getComponent(ProjectTracker.class);
  }
  public static class FactoryItself implements ToolWindowFactory, DumbAware {
    public void createToolWindowContent(final Project project, ToolWindow toolWindow) {
      final ProjectTracker tracker = getProjectComponent(project);

      SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);
      panel.setContent(tracker.myConsoleView.getComponent());

      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new DumbAwareAction("Settings", "Edit notification settings", IconLoader.getIcon("/general/secondaryGroup.png")) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          ShowSettingsUtil.getInstance().editConfigurable(project, new NotificationsConfigurable());
        }
      });
      group.addAll(ContainerUtil.subList(Arrays.asList(tracker.myConsoleView.createConsoleActions()), 2)); // no next/prev
      ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, false);
      toolbar.setTargetComponent(panel);
      panel.setToolbar(toolbar.getComponent());

      final Content content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false);
      toolWindow.getContentManager().addContent(content);
    }

  }


}
