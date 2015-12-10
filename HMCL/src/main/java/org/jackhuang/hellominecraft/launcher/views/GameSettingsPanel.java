/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hellominecraft.launcher.views;

import java.awt.Color;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.settings.LauncherVisibility;
import org.jackhuang.hellominecraft.launcher.utils.installers.InstallerVersionList;
import org.jackhuang.hellominecraft.launcher.utils.installers.InstallerVersionList.InstallerVersion;
import org.jackhuang.hellominecraft.launcher.settings.Profile;
import org.jackhuang.hellominecraft.launcher.settings.Settings;
import org.jackhuang.hellominecraft.launcher.utils.FileNameFilter;
import org.jackhuang.hellominecraft.launcher.utils.ModInfo;
import org.jackhuang.hellominecraft.launcher.version.GameDirType;
import org.jackhuang.hellominecraft.launcher.version.MinecraftVersion;
import org.jackhuang.hellominecraft.tasks.TaskRunnable;
import org.jackhuang.hellominecraft.tasks.TaskRunnableArg1;
import org.jackhuang.hellominecraft.tasks.TaskWindow;
import org.jackhuang.hellominecraft.tasks.communication.DefaultPreviousResult;
import org.jackhuang.hellominecraft.utils.system.IOUtils;
import org.jackhuang.hellominecraft.utils.MessageBox;
import org.jackhuang.hellominecraft.version.MinecraftVersionRequest;
import org.jackhuang.hellominecraft.utils.system.OS;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.utils.SwingUtils;
import org.jackhuang.hellominecraft.utils.system.Java;
import org.jackhuang.hellominecraft.views.LogWindow;
import rx.Observable;
import rx.concurrency.Schedulers;

/**
 *
 * @author huangyuhui
 */
public final class GameSettingsPanel extends javax.swing.JPanel implements DropTargetListener {

    boolean isLoading = false;
    public MinecraftVersionRequest minecraftVersion;
    InstallerHelper forge, optifine, liteloader;
    String mcVersion;

    /**
     * Creates new form GameSettingsPanel
     */
    public GameSettingsPanel() {
        initComponents();
        setBackground(Color.white);
        setOpaque(true);

        forge = new InstallerHelper(lstForge, "forge");
        liteloader = new InstallerHelper(lstLiteLoader, "liteloader");
        optifine = new InstallerHelper(lstOptifine, "optifine");
        initExplorationMenu();
        initManagementMenu();
        initExternalModsTable();
        initTabs();

        for (Java j : Settings.JAVA)
            cboJava.addItem(j.getLocalizedName());

        dropTarget = new DropTarget(lstExternalMods, DnDConstants.ACTION_COPY_OR_MOVE, this);
    }

    void initExplorationMenu() {
        ppmExplore = new JPopupMenu();
        class ImplementedActionListener implements ActionListener {

            String a;

            ImplementedActionListener(String s) {
                a = s;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                getProfile().getMinecraftProvider().open(mcVersion, a);
            }
        }
        JMenuItem itm;
        itm = new JMenuItem(C.i18n("folder.game"));
        itm.addActionListener(new ImplementedActionListener(null));
        ppmExplore.add(itm);
        itm = new JMenuItem(C.i18n("folder.mod"));
        itm.addActionListener(new ImplementedActionListener("mods"));
        ppmExplore.add(itm);
        itm = new JMenuItem(C.i18n("folder.coremod"));
        itm.addActionListener(new ImplementedActionListener("coremods"));
        ppmExplore.add(itm);
        itm = new JMenuItem(C.i18n("folder.config"));
        itm.addActionListener(new ImplementedActionListener("config"));
        ppmExplore.add(itm);
        itm = new JMenuItem(C.i18n("folder.resourcepacks"));
        itm.addActionListener(new ImplementedActionListener("resourcepacks"));
        ppmExplore.add(itm);
        itm = new JMenuItem(C.i18n("folder.screenshots"));
        itm.addActionListener(new ImplementedActionListener("screenshots"));
        ppmExplore.add(itm);
    }

    void initManagementMenu() {
        ppmManage = new JPopupMenu();
        JMenuItem itm = new JMenuItem(C.i18n("versions.manage.rename"));
        itm.addActionListener((e) -> {
            if (mcVersion != null) {
                String newName = JOptionPane.showInputDialog(C.i18n("versions.manage.rename.message"), mcVersion);
                if (newName != null)
                    if (getProfile().getMinecraftProvider().renameVersion(mcVersion, newName))
                        refreshVersions();
            }
        });
        ppmManage.add(itm);
        itm = new JMenuItem(C.i18n("versions.manage.remove"));
        itm.addActionListener((e) -> {
            if (mcVersion != null && MessageBox.Show(C.i18n("versions.manage.remove.confirm") + mcVersion, MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
                if (getProfile().getMinecraftProvider().removeVersionFromDisk(mcVersion))
                    refreshVersions();
        });
        ppmManage.add(itm);
        itm = new JMenuItem(C.i18n("versions.manage.redownload_json"));
        itm.addActionListener((e) -> {
            if (mcVersion != null)
                getProfile().getMinecraftProvider().refreshJson(mcVersion);
        });
        ppmManage.add(itm);
        itm = new JMenuItem(C.i18n("versions.manage.redownload_assets_index"));
        itm.addActionListener((e) -> {
            if (mcVersion != null)
                getProfile().getMinecraftProvider().getAssetService().refreshAssetsIndex(mcVersion);
        });
        ppmManage.add(itm);
    }

    void initExternalModsTable() {
        if (lstExternalMods.getColumnModel().getColumnCount() > 0) {
            lstExternalMods.getColumnModel().getColumn(0).setMinWidth(17);
            lstExternalMods.getColumnModel().getColumn(0).setPreferredWidth(17);
            lstExternalMods.getColumnModel().getColumn(0).setMaxWidth(17);
        }
        lstExternalMods.getSelectionModel().addListSelectionListener(e -> {
            int row = lstExternalMods.getSelectedRow();
            List<ModInfo> mods = getProfile().getMinecraftProvider().getModService().getMods();
            if (mods != null && 0 <= row && row < mods.size()) {
                ModInfo m = mods.get(row);
                boolean hasLink = m.url != null;
                String text = "<html>" + (hasLink ? "<a href=\"" + m.url + "\">" : "") + m.getName() + (hasLink ? "</a>" : "");
                text += " by " + m.getAuthor();
                String description = "No mod description found";
                if (m.description != null) {
                    description = "";
                    for (String desc : m.description.split("\n"))
                        description += SwingUtils.getParsedJPanelText(lblModInfo, desc) + "<br/>";
                }
                text += "<br>" + description;
                lblModInfo.setText(text);
                lblModInfo.setCursor(new java.awt.Cursor(hasLink ? java.awt.Cursor.HAND_CURSOR : java.awt.Cursor.DEFAULT_CURSOR));
            }
        });
        ((DefaultTableModel) lstExternalMods.getModel()).addTableModelListener(e -> {
            if (e.getType() == TableModelEvent.UPDATE && e.getColumn() == 0) {
                int row = lstExternalMods.getSelectedRow();
                List<ModInfo> mods = getProfile().getMinecraftProvider().getModService().getMods();
                if (mods != null && mods.size() > row && row >= 0)
                    mods.get(row).reverseModState();
            }
        });
    }

    void initTabs() {
        tabVersionEdit.addChangeListener(new ChangeListener() {
            boolean a = false, b = false;

            @Override
            public void stateChanged(ChangeEvent e) {
                if (tabVersionEdit.getSelectedComponent() == pnlGameDownloads && !a) {
                    a = true;
                    refreshDownloads();
                } else if (tabVersionEdit.getSelectedComponent() == pnlAutoInstall && !b) {
                    b = true;
                    forge.refreshVersions();
                }
            }
        });
        tabInstallers.addChangeListener(new ChangeListener() {
            boolean refreshed[] = new boolean[] {false, false, false};
            InstallerHelper helpers[] = new InstallerHelper[] {forge, optifine, liteloader};

            @Override
            public void stateChanged(ChangeEvent e) {
                int idx = tabInstallers.getSelectedIndex();
                if (0 <= idx && idx < 3 && !refreshed[idx]) {
                    helpers[idx].refreshVersions();
                    refreshed[idx] = true;
                }
            }
        });
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        tabVersionEdit = new javax.swing.JTabbedPane();
        jPanel22 = new javax.swing.JPanel();
        jLabel24 = new javax.swing.JLabel();
        txtGameDir = new javax.swing.JTextField();
        jLabel25 = new javax.swing.JLabel();
        txtWidth = new javax.swing.JTextField();
        txtHeight = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        chkFullscreen = new javax.swing.JCheckBox();
        txtJavaDir = new javax.swing.JTextField();
        jLabel11 = new javax.swing.JLabel();
        jLabel27 = new javax.swing.JLabel();
        txtMaxMemory = new javax.swing.JTextField();
        lblMaxMemory = new javax.swing.JLabel();
        btnDownloadAllAssets = new javax.swing.JButton();
        cboLauncherVisibility = new javax.swing.JComboBox();
        jLabel10 = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        cboGameDirType = new javax.swing.JComboBox();
        btnChoosingJavaDir = new javax.swing.JButton();
        cboJava = new javax.swing.JComboBox();
        btnChoosingGameDir = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();
        chkDebug = new javax.swing.JCheckBox();
        jLabel26 = new javax.swing.JLabel();
        txtJavaArgs = new javax.swing.JTextField();
        txtMinecraftArgs = new javax.swing.JTextField();
        jLabel28 = new javax.swing.JLabel();
        jLabel29 = new javax.swing.JLabel();
        txtPermSize = new javax.swing.JTextField();
        chkNoJVMArgs = new javax.swing.JCheckBox();
        chkCancelWrapper = new javax.swing.JCheckBox();
        jLabel30 = new javax.swing.JLabel();
        txtPrecalledCommand = new javax.swing.JTextField();
        jLabel31 = new javax.swing.JLabel();
        txtServerIP = new javax.swing.JTextField();
        jPanel6 = new javax.swing.JPanel();
        jPanel7 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        lstExternalMods = new javax.swing.JTable();
        btnAddMod = new javax.swing.JButton();
        btnRemoveMod = new javax.swing.JButton();
        lblModInfo = new javax.swing.JLabel();
        pnlAutoInstall = new javax.swing.JPanel();
        tabInstallers = new javax.swing.JTabbedPane();
        jPanel16 = new javax.swing.JPanel();
        jScrollPane11 = new javax.swing.JScrollPane();
        lstForge = new javax.swing.JTable();
        btnRefreshForge = new javax.swing.JButton();
        btnDownloadForge = new javax.swing.JButton();
        pnlOptifine = new javax.swing.JPanel();
        jScrollPane13 = new javax.swing.JScrollPane();
        lstOptifine = new javax.swing.JTable();
        btnRefreshOptifine = new javax.swing.JButton();
        btnDownloadOptifine = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        btnInstallLiteLoader = new javax.swing.JButton();
        jScrollPane12 = new javax.swing.JScrollPane();
        lstLiteLoader = new javax.swing.JTable();
        btnRefreshLiteLoader = new javax.swing.JButton();
        pnlGameDownloads = new javax.swing.JPanel();
        btnDownload = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        lstDownloads = new javax.swing.JTable();
        btnRefreshGameDownloads = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        jPanel4 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        cboProfiles = new javax.swing.JComboBox();
        cboVersions = new javax.swing.JComboBox();
        jLabel2 = new javax.swing.JLabel();
        jPanel5 = new javax.swing.JPanel();
        btnModify = new javax.swing.JButton();
        btnRefreshVersions = new javax.swing.JButton();
        txtMinecraftVersion = new javax.swing.JTextField();
        btnNewProfile = new javax.swing.JButton();
        btnRemoveProfile = new javax.swing.JButton();
        btnExplore = new javax.swing.JButton();
        btnIncludeMinecraft = new javax.swing.JButton();
        btnMakeLaunchScript = new javax.swing.JButton();
        btnShowLog = new javax.swing.JButton();
        btnCleanGame = new javax.swing.JButton();

        setBackground(new java.awt.Color(255, 255, 255));

        tabVersionEdit.setName("tabVersionEdit"); // NOI18N

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/jackhuang/hellominecraft/launcher/I18N"); // NOI18N
        jLabel24.setText(bundle.getString("settings.game_directory")); // NOI18N

        txtGameDir.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtGameDirFocusLost(evt);
            }
        });

        jLabel25.setText(bundle.getString("settings.dimension")); // NOI18N

        txtWidth.setToolTipText("");
        txtWidth.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtWidthFocusLost(evt);
            }
        });

        txtHeight.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtHeightFocusLost(evt);
            }
        });

        jLabel9.setText("x");

        chkFullscreen.setText(bundle.getString("settings.fullscreen")); // NOI18N
        chkFullscreen.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                chkFullscreenFocusLost(evt);
            }
        });

        txtJavaDir.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtJavaDirFocusLost(evt);
            }
        });

        jLabel11.setText(bundle.getString("settings.java_dir")); // NOI18N

        jLabel27.setText(bundle.getString("settings.max_memory")); // NOI18N

        txtMaxMemory.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtMaxMemoryFocusLost(evt);
            }
        });

        lblMaxMemory.setText(C.i18n("settings.physical_memory") + ": " + OS.getTotalPhysicalMemory() / 1024 / 1024 + "MB");

        btnDownloadAllAssets.setText(bundle.getString("assets.download_all")); // NOI18N
        btnDownloadAllAssets.setToolTipText("");
        btnDownloadAllAssets.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDownloadAllAssetsActionPerformed(evt);
            }
        });

        cboLauncherVisibility.setModel(new javax.swing.DefaultComboBoxModel(new String[] { C.I18N.getString("advancedsettings.launcher_visibility.close"), C.I18N.getString("advancedsettings.launcher_visibility.hide"), C.I18N.getString("advancedsettings.launcher_visibility.keep") }));
        cboLauncherVisibility.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                cboLauncherVisibilityFocusLost(evt);
            }
        });

        jLabel10.setText(bundle.getString("advancedsettings.launcher_visible")); // NOI18N

        jLabel12.setText(bundle.getString("advancedsettings.run_directory")); // NOI18N

        cboGameDirType.setModel(new javax.swing.DefaultComboBoxModel(new String[] { C.I18N.getString("advancedsettings.game_dir.default"), C.I18N.getString("advancedsettings.game_dir.independent") }));
        cboGameDirType.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                cboGameDirTypeFocusLost(evt);
            }
        });

        btnChoosingJavaDir.setText(bundle.getString("ui.button.explore")); // NOI18N
        btnChoosingJavaDir.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnChoosingJavaDirActionPerformed(evt);
            }
        });

        cboJava.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cboJavaItemStateChanged(evt);
            }
        });

        btnChoosingGameDir.setText(bundle.getString("ui.button.explore")); // NOI18N
        btnChoosingGameDir.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnChoosingGameDirActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel22Layout = new javax.swing.GroupLayout(jPanel22);
        jPanel22.setLayout(jPanel22Layout);
        jPanel22Layout.setHorizontalGroup(
            jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel22Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel22Layout.createSequentialGroup()
                        .addComponent(btnDownloadAllAssets)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(jPanel22Layout.createSequentialGroup()
                        .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel11)
                            .addComponent(jLabel27)
                            .addComponent(jLabel24)
                            .addComponent(jLabel12)
                            .addComponent(jLabel10)
                            .addComponent(jLabel25))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(cboGameDirType, javax.swing.GroupLayout.Alignment.TRAILING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(cboLauncherVisibility, javax.swing.GroupLayout.Alignment.TRAILING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(jPanel22Layout.createSequentialGroup()
                                .addComponent(txtWidth, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel9)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(txtHeight, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 306, Short.MAX_VALUE)
                                .addComponent(chkFullscreen))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel22Layout.createSequentialGroup()
                                .addComponent(txtMaxMemory)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(lblMaxMemory))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel22Layout.createSequentialGroup()
                                .addComponent(txtGameDir)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btnChoosingGameDir))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel22Layout.createSequentialGroup()
                                .addComponent(cboJava, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(txtJavaDir)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btnChoosingJavaDir)))))
                .addContainerGap())
        );
        jPanel22Layout.setVerticalGroup(
            jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel22Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtGameDir, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel24)
                    .addComponent(btnChoosingGameDir, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtJavaDir, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel11)
                    .addComponent(btnChoosingJavaDir, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(cboJava, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblMaxMemory)
                    .addComponent(txtMaxMemory, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel27))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cboLauncherVisibility, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel10))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cboGameDirType, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel12))
                .addGap(4, 4, 4)
                .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtHeight, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(chkFullscreen, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel9, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel25)
                    .addComponent(txtWidth, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 95, Short.MAX_VALUE)
                .addComponent(btnDownloadAllAssets)
                .addContainerGap())
        );

        tabVersionEdit.addTab(bundle.getString("settings"), jPanel22); // NOI18N

        chkDebug.setText(bundle.getString("advencedsettings.debug_mode")); // NOI18N
        chkDebug.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                chkDebugFocusLost(evt);
            }
        });

        jLabel26.setText(bundle.getString("advancedsettings.jvm_args")); // NOI18N

        txtJavaArgs.setToolTipText(bundle.getString("advancedsettings.java_args_default")); // NOI18N
        txtJavaArgs.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtJavaArgsFocusLost(evt);
            }
        });

        txtMinecraftArgs.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtMinecraftArgsFocusLost(evt);
            }
        });

        jLabel28.setText(bundle.getString("advancedsettings.Minecraft_arguments")); // NOI18N

        jLabel29.setText(bundle.getString("advancedsettings.java_permanent_generation_space")); // NOI18N

        txtPermSize.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtPermSizeFocusLost(evt);
            }
        });

        chkNoJVMArgs.setText(bundle.getString("advancedsettings.no_jvm_args")); // NOI18N
        chkNoJVMArgs.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                chkNoJVMArgsFocusLost(evt);
            }
        });

        chkCancelWrapper.setText(bundle.getString("advancedsettings.cancel_wrapper_launcher")); // NOI18N
        chkCancelWrapper.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                chkCancelWrapperFocusLost(evt);
            }
        });

        jLabel30.setText(bundle.getString("advancedsettings.wrapper_launcher")); // NOI18N

        txtPrecalledCommand.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtPrecalledCommandFocusLost(evt);
            }
        });

        jLabel31.setText(bundle.getString("advancedsettings.server_ip")); // NOI18N

        txtServerIP.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtServerIPFocusLost(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(txtPrecalledCommand)
                    .addComponent(txtServerIP)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel30)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(chkDebug)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(chkCancelWrapper)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(chkNoJVMArgs))
                            .addComponent(jLabel31))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel28)
                            .addComponent(jLabel29)
                            .addComponent(jLabel26))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(txtJavaArgs)
                            .addComponent(txtMinecraftArgs)
                            .addComponent(txtPermSize, javax.swing.GroupLayout.Alignment.TRAILING))))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtJavaArgs, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel26))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtMinecraftArgs, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel28))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtPermSize, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel29))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel30)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtPrecalledCommand, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel31)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtServerIP, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 90, Short.MAX_VALUE)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(chkDebug)
                    .addComponent(chkNoJVMArgs)
                    .addComponent(chkCancelWrapper))
                .addContainerGap())
        );

        tabVersionEdit.addTab(bundle.getString("advancedsettings"), jPanel2); // NOI18N

        lstExternalMods.setModel(SwingUtils.makeDefaultTableModel(new String[]{"", "Mod", C.i18n("ui.label.version")}, new Class[]{Boolean.class,String.class,String.class}, new boolean[]{true,false,false}));
        lstExternalMods.setColumnSelectionAllowed(true);
        lstExternalMods.getTableHeader().setReorderingAllowed(false);
        lstExternalMods.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                lstExternalModsKeyPressed(evt);
            }
        });
        jScrollPane1.setViewportView(lstExternalMods);
        lstExternalMods.getColumnModel().getSelectionModel().setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

        btnAddMod.setText(C.i18n("mods.add")); // NOI18N
        btnAddMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddModActionPerformed(evt);
            }
        });

        btnRemoveMod.setText(C.i18n("mods.remove")); // NOI18N
        btnRemoveMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRemoveModActionPerformed(evt);
            }
        });

        lblModInfo.setText(bundle.getString("mods.default_information")); // NOI18N
        lblModInfo.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        lblModInfo.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lblModInfoMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 592, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(btnRemoveMod)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel7Layout.createSequentialGroup()
                        .addComponent(btnAddMod)
                        .addContainerGap())))
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(lblModInfo)
                .addContainerGap())
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addComponent(btnAddMod)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnRemoveMod)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 264, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblModInfo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        tabVersionEdit.addTab(bundle.getString("mods"), jPanel6); // NOI18N

        lstForge.setModel(SwingUtils.makeDefaultTableModel(new String[]{C.I18N.getString("install.version"), C.I18N.getString("install.mcversion")},
            new Class[]{String.class, String.class}, new boolean[]{false, false}));
    lstForge.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    jScrollPane11.setViewportView(lstForge);

    btnRefreshForge.setText(bundle.getString("ui.button.refresh")); // NOI18N
    btnRefreshForge.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnRefreshForgeActionPerformed(evt);
        }
    });

    btnDownloadForge.setText(bundle.getString("ui.button.install")); // NOI18N
    btnDownloadForge.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnDownloadForgeActionPerformed(evt);
        }
    });

    javax.swing.GroupLayout jPanel16Layout = new javax.swing.GroupLayout(jPanel16);
    jPanel16.setLayout(jPanel16Layout);
    jPanel16Layout.setHorizontalGroup(
        jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel16Layout.createSequentialGroup()
            .addComponent(jScrollPane11, javax.swing.GroupLayout.DEFAULT_SIZE, 587, Short.MAX_VALUE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                .addComponent(btnDownloadForge, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(btnRefreshForge, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addContainerGap())
    );
    jPanel16Layout.setVerticalGroup(
        jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addComponent(jScrollPane11, javax.swing.GroupLayout.DEFAULT_SIZE, 296, Short.MAX_VALUE)
        .addGroup(jPanel16Layout.createSequentialGroup()
            .addComponent(btnDownloadForge)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(btnRefreshForge)
            .addGap(0, 0, Short.MAX_VALUE))
    );

    tabInstallers.addTab("Forge", jPanel16);

    lstOptifine.setModel(SwingUtils.makeDefaultTableModel(new String[]{C.I18N.getString("install.version"), C.I18N.getString("install.mcversion")},
        new Class[]{String.class, String.class}, new boolean[]{false, false}));
lstOptifine.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
jScrollPane13.setViewportView(lstOptifine);

btnRefreshOptifine.setText(bundle.getString("ui.button.refresh")); // NOI18N
btnRefreshOptifine.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        btnRefreshOptifineActionPerformed(evt);
    }
    });

    btnDownloadOptifine.setText(bundle.getString("ui.button.install")); // NOI18N
    btnDownloadOptifine.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnDownloadOptifineActionPerformed(evt);
        }
    });

    javax.swing.GroupLayout pnlOptifineLayout = new javax.swing.GroupLayout(pnlOptifine);
    pnlOptifine.setLayout(pnlOptifineLayout);
    pnlOptifineLayout.setHorizontalGroup(
        pnlOptifineLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(pnlOptifineLayout.createSequentialGroup()
            .addComponent(jScrollPane13, javax.swing.GroupLayout.DEFAULT_SIZE, 587, Short.MAX_VALUE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(pnlOptifineLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                .addComponent(btnDownloadOptifine, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(btnRefreshOptifine))
            .addContainerGap())
    );
    pnlOptifineLayout.setVerticalGroup(
        pnlOptifineLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addComponent(jScrollPane13, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
        .addGroup(pnlOptifineLayout.createSequentialGroup()
            .addComponent(btnDownloadOptifine)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(btnRefreshOptifine)
            .addGap(0, 244, Short.MAX_VALUE))
    );

    tabInstallers.addTab("OptiFine", pnlOptifine);

    btnInstallLiteLoader.setText(bundle.getString("ui.button.install")); // NOI18N
    btnInstallLiteLoader.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnInstallLiteLoaderActionPerformed(evt);
        }
    });

    lstLiteLoader.setModel(SwingUtils.makeDefaultTableModel(new String[]{C.I18N.getString("install.version"), C.I18N.getString("install.mcversion")},
        new Class[]{String.class, String.class}, new boolean[]{false, false}));
lstLiteLoader.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
jScrollPane12.setViewportView(lstLiteLoader);

btnRefreshLiteLoader.setText(bundle.getString("ui.button.refresh")); // NOI18N
btnRefreshLiteLoader.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        btnRefreshLiteLoaderActionPerformed(evt);
    }
    });

    javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
    jPanel3.setLayout(jPanel3Layout);
    jPanel3Layout.setHorizontalGroup(
        jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(jPanel3Layout.createSequentialGroup()
            .addComponent(jScrollPane12, javax.swing.GroupLayout.DEFAULT_SIZE, 587, Short.MAX_VALUE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                .addComponent(btnInstallLiteLoader, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(btnRefreshLiteLoader))
            .addContainerGap())
    );
    jPanel3Layout.setVerticalGroup(
        jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addComponent(jScrollPane12, javax.swing.GroupLayout.DEFAULT_SIZE, 296, Short.MAX_VALUE)
        .addGroup(jPanel3Layout.createSequentialGroup()
            .addComponent(btnInstallLiteLoader)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(btnRefreshLiteLoader)
            .addGap(0, 0, Short.MAX_VALUE))
    );

    tabInstallers.addTab("LiteLoader", jPanel3);

    javax.swing.GroupLayout pnlAutoInstallLayout = new javax.swing.GroupLayout(pnlAutoInstall);
    pnlAutoInstall.setLayout(pnlAutoInstallLayout);
    pnlAutoInstallLayout.setHorizontalGroup(
        pnlAutoInstallLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addComponent(tabInstallers)
    );
    pnlAutoInstallLayout.setVerticalGroup(
        pnlAutoInstallLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addComponent(tabInstallers)
    );

    tabVersionEdit.addTab(bundle.getString("settings.tabs.installers"), pnlAutoInstall); // NOI18N

    btnDownload.setText(bundle.getString("ui.button.download")); // NOI18N
    btnDownload.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnDownloadActionPerformed(evt);
        }
    });

    lstDownloads.setModel(SwingUtils.makeDefaultTableModel(new String[]{C.I18N.getString("install.version"), C.I18N.getString("install.time"), C.I18N.getString("install.type")},new Class[]{String.class, String.class, String.class}, new boolean[]{false, false, false}));
    lstDownloads.setToolTipText("");
    lstDownloads.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    jScrollPane2.setViewportView(lstDownloads);

    btnRefreshGameDownloads.setText(bundle.getString("ui.button.refresh")); // NOI18N
    btnRefreshGameDownloads.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnRefreshGameDownloadsActionPerformed(evt);
        }
    });

    javax.swing.GroupLayout pnlGameDownloadsLayout = new javax.swing.GroupLayout(pnlGameDownloads);
    pnlGameDownloads.setLayout(pnlGameDownloadsLayout);
    pnlGameDownloadsLayout.setHorizontalGroup(
        pnlGameDownloadsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(pnlGameDownloadsLayout.createSequentialGroup()
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 592, Short.MAX_VALUE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(pnlGameDownloadsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                .addComponent(btnRefreshGameDownloads, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(btnDownload))
            .addContainerGap())
    );
    pnlGameDownloadsLayout.setVerticalGroup(
        pnlGameDownloadsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(pnlGameDownloadsLayout.createSequentialGroup()
            .addComponent(btnRefreshGameDownloads)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(btnDownload))
        .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 325, Short.MAX_VALUE)
    );

    tabVersionEdit.addTab(bundle.getString("settings.tabs.game_download"), pnlGameDownloads); // NOI18N

    jLabel1.setText(bundle.getString("ui.label.profile")); // NOI18N

    cboProfiles.setMinimumSize(new java.awt.Dimension(32, 23));
    cboProfiles.setPreferredSize(new java.awt.Dimension(32, 23));
    cboProfiles.addItemListener(new java.awt.event.ItemListener() {
        public void itemStateChanged(java.awt.event.ItemEvent evt) {
            cboProfilesItemStateChanged(evt);
        }
    });

    cboVersions.addItemListener(new java.awt.event.ItemListener() {
        public void itemStateChanged(java.awt.event.ItemEvent evt) {
            cboVersionsItemStateChanged(evt);
        }
    });

    jLabel2.setText(bundle.getString("ui.label.version")); // NOI18N

    javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
    jPanel4.setLayout(jPanel4Layout);
    jPanel4Layout.setHorizontalGroup(
        jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(jPanel4Layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel4Layout.createSequentialGroup()
                    .addComponent(jLabel1)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(cboProfiles, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGroup(jPanel4Layout.createSequentialGroup()
                    .addComponent(jLabel2)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(cboVersions, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
    );
    jPanel4Layout.setVerticalGroup(
        jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(jPanel4Layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(cboProfiles, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(jLabel1))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(cboVersions, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(jLabel2))
            .addContainerGap(11, Short.MAX_VALUE))
    );

    btnModify.setText(bundle.getString("settings.manage")); // NOI18N
    btnModify.addMouseListener(new java.awt.event.MouseAdapter() {
        public void mouseClicked(java.awt.event.MouseEvent evt) {
            btnModifyMouseClicked(evt);
        }
    });

    btnRefreshVersions.setText(bundle.getString("ui.button.refresh")); // NOI18N
    btnRefreshVersions.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnRefreshVersionsActionPerformed(evt);
        }
    });

    txtMinecraftVersion.setEditable(false);

    btnNewProfile.setText(bundle.getString("setupwindow.new")); // NOI18N
    btnNewProfile.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnNewProfileActionPerformed(evt);
        }
    });

    btnRemoveProfile.setText(bundle.getString("ui.button.delete")); // NOI18N
    btnRemoveProfile.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnRemoveProfileActionPerformed(evt);
        }
    });

    btnExplore.setText(bundle.getString("settings.explore")); // NOI18N
    btnExplore.setToolTipText("");
    btnExplore.addMouseListener(new java.awt.event.MouseAdapter() {
        public void mouseClicked(java.awt.event.MouseEvent evt) {
            btnExploreMouseClicked(evt);
        }
    });

    javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
    jPanel5.setLayout(jPanel5Layout);
    jPanel5Layout.setHorizontalGroup(
        jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(jPanel5Layout.createSequentialGroup()
            .addGap(0, 0, 0)
            .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                .addComponent(btnNewProfile, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(txtMinecraftVersion))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                .addComponent(btnRemoveProfile, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(btnRefreshVersions, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                .addComponent(btnModify, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(btnExplore, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
    );
    jPanel5Layout.setVerticalGroup(
        jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(jPanel5Layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(btnNewProfile, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(btnRemoveProfile, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(btnExplore, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(txtMinecraftVersion, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(btnRefreshVersions, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(btnModify, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
    );

    javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
    jPanel1.setLayout(jPanel1Layout);
    jPanel1Layout.setHorizontalGroup(
        jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(jPanel1Layout.createSequentialGroup()
            .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
    );
    jPanel1Layout.setVerticalGroup(
        jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(jPanel1Layout.createSequentialGroup()
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGap(0, 0, Short.MAX_VALUE))
    );

    btnIncludeMinecraft.setText(bundle.getString("setupwindow.include_minecraft")); // NOI18N
    btnIncludeMinecraft.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnIncludeMinecraftActionPerformed(evt);
        }
    });

    btnMakeLaunchScript.setText(bundle.getString("mainwindow.make_launch_script")); // NOI18N
    btnMakeLaunchScript.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnMakeLaunchScriptActionPerformed(evt);
        }
    });

    btnShowLog.setText(bundle.getString("mainwindow.show_log")); // NOI18N
    btnShowLog.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnShowLogActionPerformed(evt);
        }
    });

    btnCleanGame.setText(bundle.getString("setupwindow.clean")); // NOI18N
    btnCleanGame.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnCleanGameActionPerformed(evt);
        }
    });

    javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
    this.setLayout(layout);
    layout.setHorizontalGroup(
        layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        .addGroup(layout.createSequentialGroup()
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(btnIncludeMinecraft)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnCleanGame)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(btnShowLog)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(btnMakeLaunchScript))
                .addComponent(tabVersionEdit))
            .addContainerGap())
    );
    layout.setVerticalGroup(
        layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(layout.createSequentialGroup()
            .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(tabVersionEdit)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(btnIncludeMinecraft)
                .addComponent(btnMakeLaunchScript)
                .addComponent(btnShowLog)
                .addComponent(btnCleanGame))
            .addContainerGap())
    );
    }// </editor-fold>//GEN-END:initComponents
    // <editor-fold defaultstate="collapsed" desc="UI Events">    
    private void cboProfilesItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboProfilesItemStateChanged
        if (isLoading)
            return;
        if (getProfile().getMinecraftProvider().getVersionCount() <= 0)
            versionChanged(null);
        prepare(getProfile());
    }//GEN-LAST:event_cboProfilesItemStateChanged

    private void btnNewProfileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnNewProfileActionPerformed
        NewProfileWindow window = new NewProfileWindow(null);
        window.setVisible(true);
        loadProfiles();
    }//GEN-LAST:event_btnNewProfileActionPerformed

    private void btnRemoveProfileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRemoveProfileActionPerformed
        if (MessageBox.Show(C.i18n("ui.message.sure_remove", getProfile().getName()), MessageBox.YES_NO_OPTION) == MessageBox.NO_OPTION)
            return;
        String name = getProfile().getName();
        if (Settings.delProfile(getProfile())) {
            cboProfiles.removeItem(name);
            prepare(getProfile());
            loadVersions();
        }
    }//GEN-LAST:event_btnRemoveProfileActionPerformed

    private void cboVersionsItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboVersionsItemStateChanged
        if (isLoading || evt.getStateChange() != ItemEvent.SELECTED || cboVersions.getSelectedIndex() < 0 || StrUtils.isBlank((String) cboVersions.getSelectedItem()))
            return;
        String mcv = (String) cboVersions.getSelectedItem();
        loadMinecraftVersion(mcv);
        versionChanged(mcv);
        getProfile().setSelectedMinecraftVersion(mcv);
        cboVersions.setToolTipText(mcv);
    }//GEN-LAST:event_cboVersionsItemStateChanged

    private void btnRefreshVersionsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRefreshVersionsActionPerformed
        refreshVersions();
    }//GEN-LAST:event_btnRefreshVersionsActionPerformed

    private void btnRefreshForgeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRefreshForgeActionPerformed
        forge.refreshVersions();
    }//GEN-LAST:event_btnRefreshForgeActionPerformed

    private void btnDownloadForgeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDownloadForgeActionPerformed
        forge.downloadSelectedRow();
    }//GEN-LAST:event_btnDownloadForgeActionPerformed

    private void btnRefreshOptifineActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRefreshOptifineActionPerformed
        optifine.refreshVersions();
    }//GEN-LAST:event_btnRefreshOptifineActionPerformed

    private void btnDownloadOptifineActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDownloadOptifineActionPerformed
        optifine.downloadSelectedRow();
    }//GEN-LAST:event_btnDownloadOptifineActionPerformed

    private void btnInstallLiteLoaderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnInstallLiteLoaderActionPerformed
        liteloader.downloadSelectedRow();
    }//GEN-LAST:event_btnInstallLiteLoaderActionPerformed

    private void btnRefreshLiteLoaderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRefreshLiteLoaderActionPerformed
        liteloader.refreshVersions();
    }//GEN-LAST:event_btnRefreshLiteLoaderActionPerformed

    private void btnDownloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDownloadActionPerformed
        downloadMinecraft();
        refreshVersions();
    }//GEN-LAST:event_btnDownloadActionPerformed

    private void btnRefreshGameDownloadsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRefreshGameDownloadsActionPerformed
        refreshDownloads();
    }//GEN-LAST:event_btnRefreshGameDownloadsActionPerformed

    private void btnExploreMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_btnExploreMouseClicked
        ppmExplore.show(evt.getComponent(), evt.getPoint().x, evt.getPoint().y);
    }//GEN-LAST:event_btnExploreMouseClicked

    private void btnIncludeMinecraftActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnIncludeMinecraftActionPerformed
        JFileChooser fc = new JFileChooser(IOUtils.currentDir());
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int ret = fc.showOpenDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File newGameDir = fc.getSelectedFile();
            String name = JOptionPane.showInputDialog(C.i18n("setupwindow.give_a_name"));
            if (StrUtils.isBlank(name)) {
                MessageBox.Show(C.i18n("setupwindow.no_empty_name"));
                return;
            }
            Settings.trySetProfile(new Profile(name).setGameDir(newGameDir.getAbsolutePath()));
            MessageBox.Show(C.i18n("setupwindow.find_in_configurations"));
            loadProfiles();
        }
    }//GEN-LAST:event_btnIncludeMinecraftActionPerformed

    private void btnModifyMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_btnModifyMouseClicked
        ppmManage.show(evt.getComponent(), evt.getPoint().x, evt.getPoint().y);
    }//GEN-LAST:event_btnModifyMouseClicked

    private void txtJavaArgsFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtJavaArgsFocusLost
        getProfile().setJavaArgs(txtJavaArgs.getText());
    }//GEN-LAST:event_txtJavaArgsFocusLost

    private void txtMinecraftArgsFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtMinecraftArgsFocusLost
        getProfile().setMinecraftArgs(txtMinecraftArgs.getText());
    }//GEN-LAST:event_txtMinecraftArgsFocusLost

    private void txtPermSizeFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtPermSizeFocusLost
        getProfile().setPermSize(txtPermSize.getText());
    }//GEN-LAST:event_txtPermSizeFocusLost

    private void chkDebugFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_chkDebugFocusLost
        getProfile().setDebug(chkDebug.isSelected());
    }//GEN-LAST:event_chkDebugFocusLost

    private void chkNoJVMArgsFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_chkNoJVMArgsFocusLost
        getProfile().setNoJVMArgs(chkNoJVMArgs.isSelected());
    }//GEN-LAST:event_chkNoJVMArgsFocusLost

    private void chkCancelWrapperFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_chkCancelWrapperFocusLost
        getProfile().setCanceledWrapper(chkCancelWrapper.isSelected());
    }//GEN-LAST:event_chkCancelWrapperFocusLost

    private void txtPrecalledCommandFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtPrecalledCommandFocusLost
        getProfile().setPrecalledCommand(txtPrecalledCommand.getText());
    }//GEN-LAST:event_txtPrecalledCommandFocusLost

    private void txtServerIPFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtServerIPFocusLost
        getProfile().setServerIp(txtServerIP.getText());
    }//GEN-LAST:event_txtServerIPFocusLost

    private void cboGameDirTypeFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_cboGameDirTypeFocusLost
        if (cboGameDirType.getSelectedIndex() >= 0)
            getProfile().setGameDirType(GameDirType.values()[cboGameDirType.getSelectedIndex()]);
    }//GEN-LAST:event_cboGameDirTypeFocusLost

    private void cboLauncherVisibilityFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_cboLauncherVisibilityFocusLost
        if (cboLauncherVisibility.getSelectedIndex() >= 0)
            getProfile().setLauncherVisibility(LauncherVisibility.values()[cboLauncherVisibility.getSelectedIndex()]);
    }//GEN-LAST:event_cboLauncherVisibilityFocusLost

    private void btnDownloadAllAssetsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDownloadAllAssetsActionPerformed
        if (mcVersion != null)
            getProfile().getMinecraftProvider().getAssetService().downloadAssets(mcVersion).run();
    }//GEN-LAST:event_btnDownloadAllAssetsActionPerformed

    private void txtMaxMemoryFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtMaxMemoryFocusLost
        getProfile().setMaxMemory(txtMaxMemory.getText());
    }//GEN-LAST:event_txtMaxMemoryFocusLost

    private void txtJavaDirFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtJavaDirFocusLost
        getProfile().setJavaDir(txtJavaDir.getText());
    }//GEN-LAST:event_txtJavaDirFocusLost

    private void chkFullscreenFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_chkFullscreenFocusLost
        getProfile().setFullscreen(chkFullscreen.isSelected());
    }//GEN-LAST:event_chkFullscreenFocusLost

    private void txtHeightFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtHeightFocusLost
        getProfile().setHeight(txtHeight.getText());
    }//GEN-LAST:event_txtHeightFocusLost

    private void txtWidthFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtWidthFocusLost
        getProfile().setWidth(txtWidth.getText());
    }//GEN-LAST:event_txtWidthFocusLost

    private void txtGameDirFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtGameDirFocusLost
        getProfile().setGameDir(txtGameDir.getText());
        loadVersions();
    }//GEN-LAST:event_txtGameDirFocusLost

    private void btnChoosingJavaDirActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnChoosingJavaDirActionPerformed
        if (cboJava.getSelectedIndex() != 1)
            return;
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setDialogTitle(C.i18n("settings.choose_javapath"));
        fc.setMultiSelectionEnabled(false);
        fc.setFileFilter(new FileNameFilter("javaw.exe"));
        fc.addChoosableFileFilter(new FileNameFilter("java.exe"));
        fc.addChoosableFileFilter(new FileNameFilter("java"));
        fc.showOpenDialog(this);
        if (fc.getSelectedFile() == null)
            return;
        try {
            String path = fc.getSelectedFile().getCanonicalPath();
            txtJavaDir.setText(path);
            getProfile().setJavaDir(txtJavaDir.getText());
        } catch (IOException e) {
            HMCLog.warn("Failed to set java path.", e);
            MessageBox.Show(C.i18n("ui.label.failed_set") + e.getMessage());
        }
    }//GEN-LAST:event_btnChoosingJavaDirActionPerformed

    private void cboJavaItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboJavaItemStateChanged
        if (isLoading || evt.getStateChange() != ItemEvent.SELECTED || cboJava.getSelectedIndex() < 0 || StrUtils.isBlank((String) cboJava.getSelectedItem()))
            return;
        int idx = cboJava.getSelectedIndex();
        if (idx != -1) {
            Java j = Settings.JAVA.get(idx);
            getProfile().setJava(j);
            txtJavaDir.setEnabled(idx == 1);
            txtJavaDir.setText(j.getHome() == null ? getProfile().getSettingsJavaDir() : j.getJava());
        }
    }//GEN-LAST:event_cboJavaItemStateChanged

    private void btnAddModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddModActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setDialogTitle(C.I18N.getString("mods.choose_mod"));
        fc.setMultiSelectionEnabled(true);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
            return;
        boolean flag = true;
        for (File f : fc.getSelectedFiles())
            flag &= getProfile().getMinecraftProvider().getModService().addMod(f);
        reloadMods();
        if (!flag)
            MessageBox.Show(C.I18N.getString("mods.failed"));
    }//GEN-LAST:event_btnAddModActionPerformed

    private void btnRemoveModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRemoveModActionPerformed
        getProfile().getMinecraftProvider().getModService().removeMod(lstExternalMods.getSelectedRows());
        reloadMods();
    }//GEN-LAST:event_btnRemoveModActionPerformed

    private void lstExternalModsKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_lstExternalModsKeyPressed
        if (evt.getKeyCode() == KeyEvent.VK_DELETE)
            btnRemoveModActionPerformed(null);
    }//GEN-LAST:event_lstExternalModsKeyPressed

    private void lblModInfoMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lblModInfoMouseClicked
        int idx = lstExternalMods.getSelectedRow();
        if (idx > 0 && idx < getProfile().getMinecraftProvider().getModService().getMods().size())
            getProfile().getMinecraftProvider().getModService().getMods().get(idx).showURL();
    }//GEN-LAST:event_lblModInfoMouseClicked

    private void btnChoosingGameDirActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnChoosingGameDirActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle(C.i18n("settings.choose_gamedir"));
        fc.setMultiSelectionEnabled(false);
        fc.showOpenDialog(this);
        if (fc.getSelectedFile() == null)
            return;
        try {
            String path = fc.getSelectedFile().getCanonicalPath();
            txtGameDir.setText(path);
            getProfile().setGameDir(path);
        } catch (IOException e) {
            HMCLog.warn("Failed to set game dir.", e);
            MessageBox.Show(C.i18n("ui.label.failed_set") + e.getMessage());
        }
    }//GEN-LAST:event_btnChoosingGameDirActionPerformed

    private void btnMakeLaunchScriptActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnMakeLaunchScriptActionPerformed
        MainFrame.INSTANCE.mainPanel.btnMakeLaunchCodeActionPerformed();
    }//GEN-LAST:event_btnMakeLaunchScriptActionPerformed

    private void btnShowLogActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnShowLogActionPerformed
        LogWindow.INSTANCE.setVisible(true);
    }//GEN-LAST:event_btnShowLogActionPerformed

    private void btnCleanGameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCleanGameActionPerformed
        getProfile().getMinecraftProvider().cleanFolder();
    }//GEN-LAST:event_btnCleanGameActionPerformed

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Load">
    private void loadProfiles() {
        isLoading = true;
        cboProfiles.removeAllItems();
        int index = 0, i = 0;
        for (Profile s : Settings.getProfilesFiltered()) {
            cboProfiles.addItem(s.getName());
            if (Settings.getInstance().getLast() != null && Settings.getInstance().getLast().equals(s.getName()))
                index = i;
            i++;
        }

        isLoading = false;
        if (index < cboProfiles.getItemCount()) {
            cboProfiles.setSelectedIndex(index);
            prepare(getProfile());
            loadVersions();
        }
    }

    final Profile getProfile() {
        return Settings.getProfile((String) cboProfiles.getSelectedItem());
    }

    void prepare(Profile profile) {
        if (profile == null)
            return;
        txtWidth.setText(profile.getWidth());
        txtHeight.setText(profile.getHeight());
        txtMaxMemory.setText(profile.getMaxMemory());
        txtPermSize.setText(profile.getPermSize());
        txtGameDir.setText(profile.getGameDir());
        txtJavaArgs.setText(profile.getJavaArgs());
        txtMinecraftArgs.setText(profile.getMinecraftArgs());
        txtJavaDir.setText(profile.getSettingsJavaDir());
        txtPrecalledCommand.setText(profile.getPrecalledCommand());
        txtServerIP.setText(profile.getServerIp());
        chkDebug.setSelected(profile.isDebug());
        chkNoJVMArgs.setSelected(profile.isNoJVMArgs());
        chkFullscreen.setSelected(profile.isFullscreen());
        chkCancelWrapper.setSelected(profile.isCanceledWrapper());
        cboLauncherVisibility.setSelectedIndex(profile.getLauncherVisibility().ordinal());
        cboGameDirType.setSelectedIndex(profile.getGameDirType().ordinal());

        isLoading = true;
        cboJava.setSelectedIndex(profile.getJavaIndexInAllJavas());
        isLoading = false;
        cboJavaItemStateChanged(new ItemEvent(cboJava, 0, cboJava.getSelectedItem(), ItemEvent.SELECTED));

        loadVersions();
        loadMinecraftVersion();
    }

    void loadVersions() {
        isLoading = true;
        cboVersions.removeAllItems();
        int index = 0, i = 0;
        MinecraftVersion selVersion = getProfile().getSelectedMinecraftVersion();
        String selectedMC = selVersion == null ? null : selVersion.id;
        for (MinecraftVersion each : getProfile().getMinecraftProvider().getVersions()) {
            cboVersions.addItem(each.id);
            if (each.id.equals(selectedMC))
                index = i;
            i++;
        }
        isLoading = false;
        if (index < cboVersions.getItemCount())
            cboVersions.setSelectedIndex(index);

        reloadMods();
    }

    void loadMinecraftVersion() {
        loadMinecraftVersion(getProfile().getSelectedMinecraftVersion());
    }

    void loadMinecraftVersion(String v) {
        loadMinecraftVersion(getProfile().getMinecraftProvider().getVersionById(v));
    }

    /**
     * Anaylze the jar of selected minecraft version of current getProfile() to
     * get
     * the version.
     *
     * @param v
     */
    void loadMinecraftVersion(MinecraftVersion v) {
        txtMinecraftVersion.setText("");
        if (v == null)
            return;
        minecraftVersion = MinecraftVersionRequest.minecraftVersion(v.getJar(getProfile().getGameDirFile()));
        txtMinecraftVersion.setText(MinecraftVersionRequest.getResponse(minecraftVersion));
    }

    //</editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Game Download">
    public void refreshDownloads() {
        DefaultTableModel model = SwingUtils.clearDefaultTable(lstDownloads);
        getProfile().getMinecraftProvider().getDownloadService().getRemoteVersions()
            .observeOn(Schedulers.eventQueue()).subscribeOn(Schedulers.newThread())
            .subscribe((ver) -> model.addRow(new Object[] {ver.id, ver.time,
                                                           StrUtils.equalsOne(ver.type, "old_beta", "old_alpha", "release", "snapshot") ? C.i18n("versions." + ver.type) : ver.type}),
                       (e) -> {
                           MessageBox.Show("Failed to refresh download: " + e.getLocalizedMessage());
                           HMCLog.err("Failed to refresh download.", e);
                       }, lstDownloads::updateUI);
    }

    void downloadMinecraft() {
        if (lstDownloads.getSelectedRow() < 0) {
            MessageBox.Show(C.i18n("gamedownload.not_refreshed"));
            return;
        }
        String id = (String) lstDownloads.getModel().getValueAt(lstDownloads.getSelectedRow(), 0);
        getProfile().getMinecraftProvider().getDownloadService().downloadMinecraft(id);
    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Installer">
    private String getMinecraftVersionFormatted() {
        return minecraftVersion == null ? "" : (StrUtils.formatVersion(minecraftVersion.version) == null) ? mcVersion : minecraftVersion.version;
    }

    @Override
    public void dragEnter(DropTargetDragEvent dtde) {
        DataFlavor[] f = dtde.getCurrentDataFlavors();
        if (f[0].match(DataFlavor.javaFileListFlavor))
            try {
                Transferable tr = dtde.getTransferable();
                List<File> files = (List<File>) tr.getTransferData(DataFlavor.javaFileListFlavor);
                for (File file : files)
                    getProfile().getMinecraftProvider().getModService().addMod(file);
            } catch (Exception ex) {
                HMCLog.warn("Failed to drop file.", ex);
            }
    }

    @Override
    public void dragOver(DropTargetDragEvent dtde) {
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent dtde) {
    }

    @Override
    public void dragExit(DropTargetEvent dte) {
    }

    @Override
    public void drop(DropTargetDropEvent dtde) {
    }

    class InstallerHelper {

        List<InstallerVersionList.InstallerVersion> versions;
        InstallerVersionList list;
        JTable jt;
        String id;

        public InstallerHelper(JTable jt, String id) {
            this.jt = jt;
            this.id = id;
        }

        public void loadVersions() {
            versions = loadVersions(list, jt);
        }

        void refreshVersions() {
            list = Settings.getInstance().getDownloadSource().getProvider().getInstallerByType(id);
            if (TaskWindow.getInstance().addTask(new TaskRunnableArg1<>(C.i18n("install." + id + ".get_list"), list)
                .registerPreviousResult(new DefaultPreviousResult<>(new String[] {getMinecraftVersionFormatted()})))
                .start())
                loadVersions();
        }

        public InstallerVersion getVersion(int idx) {
            return versions.get(idx);
        }

        void downloadSelectedRow() {
            int idx = jt.getSelectedRow();
            if (idx == -1) {
                MessageBox.Show(C.i18n("install.not_refreshed"));
                return;
            }
            getProfile().getInstallerService().download(getVersion(idx), id).after(new TaskRunnable(this::refreshVersions)).run();
        }

        private List<InstallerVersionList.InstallerVersion> loadVersions(InstallerVersionList list, JTable table) {
            if (list == null)
                return null;
            DefaultTableModel model = SwingUtils.clearDefaultTable(table);
            String mcver = StrUtils.formatVersion(getMinecraftVersionFormatted());
            List<InstallerVersionList.InstallerVersion> ver = list.getVersions(mcver);
            if (ver != null) {
                for (InstallerVersionList.InstallerVersion v : ver)
                    model.addRow(new Object[] {v.selfVersion == null ? "null" : v.selfVersion, v.mcVersion == null ? "null" : v.mcVersion});
                table.updateUI();
            }
            return ver;
        }
    }

    private void refreshVersions() {
        getProfile().getMinecraftProvider().refreshVersions();
        loadVersions();
    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Mods">
    private final Object lockMod = new Object();

    private void reloadMods() {
        DefaultTableModel model = SwingUtils.clearDefaultTable(lstExternalMods);
        Observable.<List<ModInfo>>createWithEmptySubscription(
            t -> t.onNext(getProfile().getMinecraftProvider().getModService().recacheMods()))
            .subscribeOn(Schedulers.newThread()).observeOn(Schedulers.eventQueue())
            .flatMap(t -> Observable.from(t))
            .subscribe(t -> model.addRow(new Object[] {t.isActive(), t.getFileName(), t.version}));
    }

    // </editor-fold>
    public void versionChanged(String version) {
        this.mcVersion = version;
        forge.loadVersions();
        optifine.loadVersions();
        liteloader.loadVersions();

        reloadMods();
    }

    public void onSelected() {
        loadProfiles();
        if (getProfile().getMinecraftProvider().getVersionCount() <= 0)
            versionChanged(null);
        else
            versionChanged((String) cboVersions.getSelectedItem());
    }

    public void showGameDownloads() {
        tabVersionEdit.setSelectedComponent(pnlGameDownloads);
    }

    // <editor-fold defaultstate="collapsed" desc="UI Definations">
    JPopupMenu ppmManage, ppmExplore;

    DropTarget dropTarget;
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAddMod;
    private javax.swing.JButton btnChoosingGameDir;
    private javax.swing.JButton btnChoosingJavaDir;
    private javax.swing.JButton btnCleanGame;
    private javax.swing.JButton btnDownload;
    private javax.swing.JButton btnDownloadAllAssets;
    private javax.swing.JButton btnDownloadForge;
    private javax.swing.JButton btnDownloadOptifine;
    private javax.swing.JButton btnExplore;
    private javax.swing.JButton btnIncludeMinecraft;
    private javax.swing.JButton btnInstallLiteLoader;
    private javax.swing.JButton btnMakeLaunchScript;
    private javax.swing.JButton btnModify;
    private javax.swing.JButton btnNewProfile;
    private javax.swing.JButton btnRefreshForge;
    private javax.swing.JButton btnRefreshGameDownloads;
    private javax.swing.JButton btnRefreshLiteLoader;
    private javax.swing.JButton btnRefreshOptifine;
    private javax.swing.JButton btnRefreshVersions;
    private javax.swing.JButton btnRemoveMod;
    private javax.swing.JButton btnRemoveProfile;
    private javax.swing.JButton btnShowLog;
    private javax.swing.JComboBox cboGameDirType;
    private javax.swing.JComboBox cboJava;
    private javax.swing.JComboBox cboLauncherVisibility;
    private javax.swing.JComboBox cboProfiles;
    private javax.swing.JComboBox cboVersions;
    private javax.swing.JCheckBox chkCancelWrapper;
    private javax.swing.JCheckBox chkDebug;
    private javax.swing.JCheckBox chkFullscreen;
    private javax.swing.JCheckBox chkNoJVMArgs;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel24;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JLabel jLabel26;
    private javax.swing.JLabel jLabel27;
    private javax.swing.JLabel jLabel28;
    private javax.swing.JLabel jLabel29;
    private javax.swing.JLabel jLabel30;
    private javax.swing.JLabel jLabel31;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel16;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel22;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane11;
    private javax.swing.JScrollPane jScrollPane12;
    private javax.swing.JScrollPane jScrollPane13;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JLabel lblMaxMemory;
    private javax.swing.JLabel lblModInfo;
    private javax.swing.JTable lstDownloads;
    private javax.swing.JTable lstExternalMods;
    private javax.swing.JTable lstForge;
    private javax.swing.JTable lstLiteLoader;
    private javax.swing.JTable lstOptifine;
    private javax.swing.JPanel pnlAutoInstall;
    private javax.swing.JPanel pnlGameDownloads;
    private javax.swing.JPanel pnlOptifine;
    private javax.swing.JTabbedPane tabInstallers;
    private javax.swing.JTabbedPane tabVersionEdit;
    private javax.swing.JTextField txtGameDir;
    private javax.swing.JTextField txtHeight;
    private javax.swing.JTextField txtJavaArgs;
    private javax.swing.JTextField txtJavaDir;
    private javax.swing.JTextField txtMaxMemory;
    private javax.swing.JTextField txtMinecraftArgs;
    private javax.swing.JTextField txtMinecraftVersion;
    private javax.swing.JTextField txtPermSize;
    private javax.swing.JTextField txtPrecalledCommand;
    private javax.swing.JTextField txtServerIP;
    private javax.swing.JTextField txtWidth;
    // End of variables declaration//GEN-END:variables
    // </editor-fold>
}
