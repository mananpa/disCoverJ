package de.itlobby.discoverj.viewcontroller;

import de.itlobby.discoverj.components.AudioListEntry;
import de.itlobby.discoverj.components.FolderListEntry;
import de.itlobby.discoverj.framework.ServiceLocator;
import de.itlobby.discoverj.framework.ViewManager;
import de.itlobby.discoverj.framework.Views;
import de.itlobby.discoverj.listeners.ListenerStateProvider;
import de.itlobby.discoverj.listeners.MultipleSelectionListener;
import de.itlobby.discoverj.listeners.ParentKeyDeletedListener;
import de.itlobby.discoverj.models.AudioWrapper;
import de.itlobby.discoverj.models.ScanResultData;
import de.itlobby.discoverj.models.SimpleAudioWrapper;
import de.itlobby.discoverj.services.InitialService;
import de.itlobby.discoverj.services.LightBoxService;
import de.itlobby.discoverj.services.SearchService;
import de.itlobby.discoverj.services.SelectionService;
import de.itlobby.discoverj.util.AudioUtil;
import de.itlobby.discoverj.util.GlyphsDude;
import de.itlobby.discoverj.util.ImageUtil;
import de.itlobby.discoverj.util.LanguageUtil;
import de.itlobby.discoverj.util.StringUtil;
import de.itlobby.discoverj.util.SystemUtil;
import de.itlobby.discoverj.util.helper.AnimationHelper;
import de.itlobby.discoverj.util.helper.AwesomeHelper;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jaudiotagger.audio.AudioFile;

import java.awt.image.BufferedImage;
import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class MainViewController implements ViewController, MultipleSelectionListener, ParentKeyDeletedListener {
    private static final Logger log = LogManager.getLogger(MainViewController.class);

    public AnchorPane rootLayout;
    public Button btnFindFolder;
    public Label txtTotalAudioCount;
    public Label txtWithCoverAudioCount;
    public Label txtWithoutCoverAudioCount;
    public Label txtFilename;
    public Label txtArtist;
    public Label txtTitle;
    public Label txtAlbum;
    public ImageView imgCurrentCover;
    public ImageView imgNewCover;
    public Label txtNewCoverResolution;
    public ProgressBar pbStatus;
    public Label txtState;
    public Label txtCurrentState;
    public Label txtMaxState;
    public Label txtCurrentAudioCoverRes;
    public HBox lightBoxLayout;
    public ProgressBar pbJavaMemory;
    public Label txtJavaCurrentMem;
    public Label txtJavaMaxMem;
    public ProgressIndicator progressScanIndicator;
    public HBox hboxMenubar;
    public TextFlow txtAudioToLoad;

    public Button btnOpenSettings;
    public Button btnExitApp;
    public Button btnOpenAbout;
    public Button btnDonate;
    public Button btnSendFeedback;
    public Button btnReportBug;
    public VBox lwAudioList;
    public ScrollPane spAudioList;
    public Button btnActionCircle;
    public Button btnActionCircleIcon;
    public Button btnRemoveCover;
    public Button btnCopyCoverToClipBrd;
    public Button btnHelpTranslate;
    public Label txtIsMixCD;
    public HBox multiSelectionLayout;
    public Button btnOpenGoogleImageSearch;

    private double totalAudioCount;
    private double currentAudioCount;
    private Text txtUpcountIndicatorValue;
    private long lastEventExcecFireTime = 0;
    private FontAwesomeIcon currentIcon;

    private ContextMenu audioListContextMenu;
    private double progressMax;
    private double progressCurrent;

    private static void createFadeAnimation(ImageView imageView) {
        FadeTransition ft = new FadeTransition(Duration.millis(500), imageView);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    public void showNewCover(BufferedImage cover, WritableImage fxImage) {
        Platform.runLater(() -> {
            imgNewCover.setImage(fxImage);
            txtNewCoverResolution.setText(String.format("(%dx%d)", cover.getWidth(), cover.getHeight()));
        });
    }

    public void activateSearchState(EventHandler<ActionEvent> stopEventHandler, int audioFilesCount) {
        activateActionButton(stopEventHandler, FontAwesomeIcon.TIMES);
        txtMaxState.setText(audioFilesCount + "");
        setProgress(0, audioFilesCount);
        unHighlightAll();
    }

    @Override
    public void initialize() {
        createIconButtons();
        initContextMenu();
        deactivateActionButton();
        createBinding();
        registerKeyListener();
        registerGeneralListener();

        ListenerStateProvider.getInstance().setMultipleSelectionListnener(this);
        ListenerStateProvider.getInstance().setParentKeyDeletedListener(this);
    }

    public void showScanResult(ScanResultData scanResultData, int withCoverCount) {
        Platform.runLater(() -> {
            hideBusyIndicator();
            setState(MessageFormat.format(LanguageUtil.getString("InitialController.thereAre0Files"),
                    scanResultData.getAudioFilesCount()));

            int without = scanResultData.getAudioFilesCount() - withCoverCount;

            txtTotalAudioCount.setText(scanResultData.getAudioFilesCount() + "");
            txtWithCoverAudioCount.setText(withCoverCount + "");
            txtWithoutCoverAudioCount.setText(without + "");

            log.info(String.format("With cover: %s\tWithout cover: %s", withCoverCount, without));

            setAudioList(scanResultData);

            if (scanResultData.getAudioFilesCount() <= 0) {
                deactivateActionButton();
            } else {
                activateActionButton(event -> ServiceLocator.get(SearchService.class)
                        .search(), FontAwesomeIcon.SEARCH);
            }
        });
    }

    public void showAudioInfo(AudioWrapper audioWrapper) {
        if (audioWrapper == null) {
            resetRightSide();
            return;
        }

        AudioFile audioFile = audioWrapper.getAudioFile();
        boolean isMixCD = AudioUtil.checkForMixCD(audioWrapper);

        Platform.runLater(() -> {
            File file = audioWrapper.getFile();
            String fileSize = StringUtil.sizeToHumanReadable(file.length());
            txtFilename.setText(String.format("%s (%s)", file.getName(), fileSize));
            txtTitle.setText(AudioUtil.getTitle(audioFile));
            txtArtist.setText(AudioUtil.getArtist(audioFile));
            txtAlbum.setText(AudioUtil.getAlbum(audioFile));
            txtIsMixCD.setVisible(isMixCD);
        });

        setAudioCoverInformation(audioFile);
    }

    private void setAudioCoverInformation(AudioFile audioFile) {
        Optional<Image> maybeCover = AudioUtil.getCover(audioFile);
        Platform.runLater(() -> {
            if (maybeCover.isPresent()) {
                imgCurrentCover.setImage(maybeCover.get());
                txtCurrentAudioCoverRes.setText(AudioUtil.getImageResolutionString(maybeCover.get()));
            } else {
                imgCurrentCover.setImage(null);
                txtCurrentAudioCoverRes.setText(null);
            }
        });
    }

    public void setProgress(int i, int audioFileListSize) {
        Platform.runLater(() -> {
            this.progressMax = audioFileListSize;
            this.progressCurrent = (i + 1);

            double value = progressCurrent / (double) audioFileListSize;
            pbStatus.setProgress(value);
            txtCurrentState.setText(i + 1 + "");
        });
    }

    public void increaseProgress() {
        Platform.runLater(() -> {
            progressCurrent = progressCurrent + 1;
            pbStatus.setProgress(progressCurrent / progressMax);
        });
    }

    private void initContextMenu() {
        audioListContextMenu = new ContextMenu();

        MenuItem alCmItemSearchGoogleImages = new MenuItem(LanguageUtil.getString("key.mainview.cm.google.image.search"), GlyphsDude.createIcon(FontAwesomeIcon.GOOGLE));
        alCmItemSearchGoogleImages.setOnAction(e -> ServiceLocator.get(InitialService.class).searchOnGoogleImages());

        MenuItem alCmItemRemoveAll = new MenuItem(LanguageUtil.getString("key.mainview.cm.remove.all"), GlyphsDude.createIcon(FontAwesomeIcon.TIMES_CIRCLE));
        alCmItemRemoveAll.setOnAction(e -> ServiceLocator.get(InitialService.class).clearAllListEntries());

        audioListContextMenu.getItems().add(alCmItemSearchGoogleImages);
        audioListContextMenu.getItems().add(alCmItemRemoveAll);

        lwAudioList.addEventHandler(
                MouseEvent.MOUSE_CLICKED,
                e ->
                {
                    if (e.getButton() == MouseButton.SECONDARY && !lwAudioList.getChildren().isEmpty()) {
                        audioListContextMenu.show(lwAudioList, e.getScreenX(), e.getScreenY());
                    } else {
                        audioListContextMenu.hide();
                    }
                }
        );
    }

    private void createIconButtons() {
        AwesomeHelper.createIconButton(btnOpenAbout, FontAwesomeIcon.INFO_CIRCLE, "menu-icon", LanguageUtil.getString("key.mainview.menu.about"), "24px");
        AwesomeHelper.createIconButton(btnReportBug, FontAwesomeIcon.BUG, "menu-icon", LanguageUtil.getString("key.mainview.menu.reportbug"), "24px");
        AwesomeHelper.createIconButton(btnHelpTranslate, FontAwesomeIcon.LANGUAGE, "menu-icon", LanguageUtil.getString("key.mainview.menu.helpTranslate"), "24px");
        AwesomeHelper.createIconButton(btnSendFeedback, FontAwesomeIcon.ENVELOPE, "menu-icon", LanguageUtil.getString("key.mainview.menu.feedback"), "24px");
        AwesomeHelper.createIconButton(btnDonate, FontAwesomeIcon.HEART, "menu-icon", LanguageUtil.getString("key.mainview.menu.donate"), "24px");
        AwesomeHelper.createIconButton(btnOpenSettings, FontAwesomeIcon.GEAR, "menu-icon", LanguageUtil.getString("key.mainview.menu.program.settings"), "24px");
        AwesomeHelper.createIconButton(btnExitApp, FontAwesomeIcon.TIMES, "menu-icon", LanguageUtil.getString("key.mainview.menu.program.shutdown"), "24px");

        AwesomeHelper.createIconButton(btnFindFolder, FontAwesomeIcon.FOLDER_OPEN, "default-icon", LanguageUtil.getString("key.mainview.open.folder"), "24px");

        AwesomeHelper.createIconButton(btnRemoveCover, FontAwesomeIcon.TRASH, "default-icon", LanguageUtil.getString("key.mainview.remove.cover"), "24px");
        AwesomeHelper.createIconButton(btnCopyCoverToClipBrd, FontAwesomeIcon.CLIPBOARD, "default-icon", LanguageUtil.getString("key.mainview.copy.clipbrd.cover"), "20px");
        AwesomeHelper.createIconButton(btnOpenGoogleImageSearch, FontAwesomeIcon.GOOGLE, "default-icon", LanguageUtil.getString("key.mainview.cm.google.image.search"), "20px");

        AwesomeHelper.createTextIcon(txtIsMixCD, FontAwesomeIcon.RANDOM, "default-icon-text", LanguageUtil.getString("key.mainview.isMixCD"), "15px");
    }

    private void registerGeneralListener() {
        lwAudioList.getChildren().addListener((ListChangeListener<Node>) c ->
        {
            if (lwAudioList.getChildren().size() > 0) {
                if (currentIcon != FontAwesomeIcon.SEARCH) {
                    activateActionButton(event -> ServiceLocator.get(SearchService.class).search(), FontAwesomeIcon.SEARCH);
                }
            } else {
                deactivateActionButton();
            }
        });
    }

    private void registerKeyListener() {
        SelectionService selectionService = ServiceLocator.get(SelectionService.class);
        InitialService initialService = ServiceLocator.get(InitialService.class);

        rootLayout.addEventHandler(KeyEvent.KEY_RELEASED, event ->
        {
            if ((System.currentTimeMillis() - lastEventExcecFireTime) > 10) {
                if (new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_ANY).match(event)) {
                    selectionService.selectAll();
                    lastEventExcecFireTime = System.currentTimeMillis();
                }
                if (new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_ANY).match(event)) {
                    initialService.findFolder();
                    lastEventExcecFireTime = System.currentTimeMillis();
                }
                if (new KeyCodeCombination(KeyCode.DELETE).match(event)) {
                    initialService.removeSelectedEntries();
                    lastEventExcecFireTime = System.currentTimeMillis();
                }
                if (new KeyCodeCombination(KeyCode.HOME).match(event)) {
                    selectionService.selectFirst();
                    lastEventExcecFireTime = System.currentTimeMillis();
                }
                if (new KeyCodeCombination(KeyCode.UP).match(event)) {
                    selectionService.selectUp();
                    lastEventExcecFireTime = System.currentTimeMillis();
                }
                if (new KeyCodeCombination(KeyCode.DOWN).match(event)) {
                    selectionService.selectDown();
                    lastEventExcecFireTime = System.currentTimeMillis();
                }
                if (new KeyCodeCombination(KeyCode.END).match(event)) {
                    selectionService.selectLast();
                    lastEventExcecFireTime = System.currentTimeMillis();
                }
                if (new KeyCodeCombination(KeyCode.HOME, KeyCombination.SHIFT_ANY).match(event)) {
                    selectionService.selectRangeToHome();
                    scrollToLastSelected();
                    lastEventExcecFireTime = System.currentTimeMillis();
                }
                if (new KeyCodeCombination(KeyCode.END, KeyCombination.SHIFT_ANY).match(event)) {
                    selectionService.selectRangeToEnd();
                    scrollToLastSelected();
                    lastEventExcecFireTime = System.currentTimeMillis();
                }
            }
        });
    }

    private void scrollToLastSelected() {
        AudioListEntry lastSelected = ServiceLocator.get(SelectionService.class).getLastSelected();

        if (lastSelected != null) {
            scrollToNodeInList(lastSelected);
        }
    }

    private void createBinding() {
        btnRemoveCover.visibleProperty().bind(imgCurrentCover.imageProperty().isNotNull());
        btnCopyCoverToClipBrd.visibleProperty().bind(imgCurrentCover.imageProperty().isNotNull());
        btnOpenGoogleImageSearch.visibleProperty().bind(imgCurrentCover.imageProperty().isNotNull());
        pbStatus.progressProperty().addListener((observable, oldValue, newValue) ->
                SystemUtil.setTaskbarProgress(newValue));
    }

    public void activateActionButton(EventHandler<ActionEvent> action, FontAwesomeIcon icon) {
        Platform.runLater(() -> {
            currentIcon = icon;

            btnActionCircle.setOnAction(action);
            btnActionCircleIcon.setOnAction(action);

            AwesomeHelper.setIconButton(btnActionCircle, FontAwesomeIcon.CIRCLE, 5, "font-button", "button-circle");
            AwesomeHelper.setIconButton(btnActionCircleIcon, icon, 2.5, "font-button", "button-circle-icon");

            AwesomeHelper.createCircleAnimation(btnActionCircle, btnActionCircleIcon, 0, 1, 50, -25);
        });
    }

    public void deactivateActionButton() {
        Platform.runLater(() -> {
            currentIcon = null;

            btnActionCircle.setOnAction(null);
            btnActionCircleIcon.setOnAction(null);

            AwesomeHelper.createCircleAnimation(btnActionCircle, btnActionCircleIcon, 1, 0, -25, 50);
        });
    }

    private void setAudioList(ScanResultData scanResultData) {
        lwAudioList.getChildren().clear();

        Map<String, List<SimpleAudioWrapper>> audioMap = scanResultData.getAudioMap();

        int iParent = 0;
        for (String parent : audioMap.keySet()) {

            FolderListEntry folderEntry = new FolderListEntry(parent);
            lwAudioList.getChildren().add(folderEntry);

            if (iParent > 0) {
                VBox.setMargin(folderEntry, new Insets(10, 0, 0, 0));
            }

            for (SimpleAudioWrapper simpleAudioWrapper : audioMap.get(parent)) {
                lwAudioList.getChildren().add(new AudioListEntry(simpleAudioWrapper));
            }

            iParent++;
        }
    }

    public void resetRightSide() {
        Platform.runLater(() -> {
            resetAudioInformation();
            resetNewCoverInformation();
        });
    }

    private void resetNewCoverInformation() {
        imgNewCover.setImage(null);
        txtNewCoverResolution.setText(null);
        pbStatus.setProgress(0);
        txtState.setText(null);
    }

    private void resetAudioInformation() {
        txtFilename.setText(null);
        txtArtist.setText(null);
        txtTitle.setText(null);
        txtAlbum.setText(null);
        imgCurrentCover.setImage(null);
        txtCurrentAudioCoverRes.setText(null);
        txtIsMixCD.setVisible(false);
    }

    public void setState(String msg) {
        Platform.runLater(() -> txtState.setText(msg));
    }

    public void showBusyIndicator(String title) {
        Platform.runLater(() -> {
            LightBoxService lightBoxService = ServiceLocator.get(LightBoxService.class);

            VBox layout = new VBox(10);
            progressScanIndicator = new ProgressIndicator();
            progressScanIndicator.setStyle("-fx-accent: #FF6F00; -fx-progress-color: #FF6F00;");

            txtAudioToLoad = new TextFlow();

            txtAudioToLoad.setTextAlignment(TextAlignment.CENTER);

            layout.getChildren().add(txtAudioToLoad);
            layout.getChildren().add(progressScanIndicator);

            VBox.setVgrow(progressScanIndicator, Priority.ALWAYS);
            VBox.setMargin(progressScanIndicator, new Insets(0, 30, 0, 30));
            VBox.setVgrow(txtAudioToLoad, Priority.NEVER);

            layout.setMinWidth(300);
            layout.setPadding(new Insets(10, 0, 10, 0));

            lightBoxService.showDialog(title, layout, null, null, false, true);
        });
    }

    public void hideBusyIndicator() {
        Platform.runLater(() -> {
            ServiceLocator.get(LightBoxService.class).hideDialog();
            progressScanIndicator = null;
        });
    }

    public void setTotalAudioCountToLoad(int totalAudioCount) {
        if (progressScanIndicator != null) {
            this.totalAudioCount = totalAudioCount;
            this.currentAudioCount = 0;
            Platform.runLater(() -> {
                Text fixPreText = new Text(LanguageUtil.getString("key.mainview.txtTotalMp3s") + " ");
                txtUpcountIndicatorValue = new Text();

                txtAudioToLoad.getChildren().add(fixPreText);
                txtAudioToLoad.getChildren().add(txtUpcountIndicatorValue);
                txtAudioToLoad.getChildren().add(new Text(String.format(" / %s", totalAudioCount)));
            });
        }
    }

    public void countIndicatorUp() {
        if (progressScanIndicator == null || txtUpcountIndicatorValue == null) {
            return;
        }

        Platform.runLater(() -> {
            currentAudioCount++;
            double value = currentAudioCount / totalAudioCount;

            txtUpcountIndicatorValue.setText(String.valueOf((int) currentAudioCount));
            progressScanIndicator.setProgress(value);
        });
    }

    public void highlightInList(SimpleAudioWrapper simpleAudioWrapper) {
        Platform.runLater(() -> {
            int i = lwAudioList.getChildren().indexOf(getAudioListEntry(simpleAudioWrapper));
            lwAudioList.getChildren().get(i).getStyleClass().add("audio-line-selected");
        });
    }

    public void highlightInList(AudioListEntry currentAudio) {
        Platform.runLater(() -> {
            int i = lwAudioList.getChildren().indexOf(currentAudio);
            lwAudioList.getChildren().get(i).getStyleClass().add("audio-line-selected");
        });
    }

    public void highlightRangeInList(AudioListEntry from, AudioListEntry to) {
        Platform.runLater(() -> {
            ArrayList<AudioListEntry> selectedEntries = new ArrayList<>();
            SimpleAudioWrapper fromWrapper = from.getSimpleAudioWrapper();
            SimpleAudioWrapper toWrapper = to.getSimpleAudioWrapper();

            boolean isInRange = false;
            List<AudioListEntry> children = lwAudioList
                    .getChildren()
                    .stream().filter(x -> x instanceof AudioListEntry)
                    .map(x -> (AudioListEntry) x)
                    .collect(Collectors.toList());

            int fromIndex = children.indexOf(from);
            int toIndex = children.indexOf(to);

            if (fromIndex > toIndex) {
                fromWrapper = to.getSimpleAudioWrapper();
                toWrapper = from.getSimpleAudioWrapper();
            }

            for (AudioListEntry entry : children) {
                if (entry.getSimpleAudioWrapper().getId().equals(fromWrapper.getId())) {
                    isInRange = true;
                }
                if (entry.getSimpleAudioWrapper().getId().equals(toWrapper.getId())) {
                    entry.getStyleClass().add("audio-line-selected");
                    selectedEntries.add(entry);
                    isInRange = false;
                }

                if (isInRange) {
                    entry.getStyleClass().add("audio-line-selected");
                    selectedEntries.add(entry);
                }
            }

            ServiceLocator.get(SelectionService.class).setSelectedEntries(selectedEntries);
        });
    }

    public void unHighlightInList(SimpleAudioWrapper currentAudio) {
        Platform.runLater(() -> {
            lwAudioList.getChildren().stream()
                    .filter(x -> x instanceof AudioListEntry)
                    .map(x -> ((AudioListEntry) x))
                    .filter(
                            x -> x.getSimpleAudioWrapper().getId().equals(currentAudio.getId())
                    )
                    .forEach(x -> x.getStyleClass().removeAll("audio-line-selected"));

            resetAudioInformation();
        });
    }

    public void unHighlightAll() {
        Platform.runLater(() ->
                lwAudioList.getChildren().stream()
                        .map(x -> ((HBox) x))
                        .forEach(x -> x.getStyleClass().removeAll("audio-line-selected")));
    }

    public void highlightAll() {
        Platform.runLater(() ->
                lwAudioList.getChildren().stream()
                        .filter(x -> x instanceof AudioListEntry)
                        .map(x -> ((AudioListEntry) x))
                        .forEach(x -> x.getStyleClass().add("audio-line-selected"))
        );
    }

    public void updateListItem(SimpleAudioWrapper currentAudio, WritableImage newCover) {
        AudioListEntry listEntry = getAudioListEntry(currentAudio);

        if (listEntry == null) {
            return;
        }

        Platform.runLater(() -> {
            listEntry.getSimpleAudioWrapper().setHasCover(true);
            createSingleLineAnimation(ImageUtil.resize(newCover, 36, 36), listEntry);
            listEntry.getIconView().setIcon(FontAwesomeIcon.CHECK);
        });
    }

    public void createSingleLineAnimation(Image coverImage, AudioListEntry entry) {
        ImageView imageView = entry.getImageView();
        if (imageView == null) {
            return;
        }

        Platform.runLater(() -> {
            createFadeAnimation(imageView);
            imageView.setImage(coverImage);
        });
    }

    public AudioListEntry getAudioListEntry(SimpleAudioWrapper currentAudio) {
        return lwAudioList.getChildren()
                .stream()
                .filter(x -> x instanceof AudioListEntry)
                .map(x -> (AudioListEntry) x)
                .filter(x -> x.getSimpleAudioWrapper().getId().equals(currentAudio.getId()))
                .findFirst()
                .orElse(null);
    }

    public void scrollToNodeInList(SimpleAudioWrapper currentAudio) {
        Platform.runLater(() -> {
            scrollTo(getAudioListEntry(currentAudio));
            ServiceLocator.get(SelectionService.class).addSelection(getAudioListEntry(currentAudio));
        });
    }

    public void scrollToNodeInList(AudioListEntry entry) {
        Platform.runLater(() ->
                scrollTo(entry)
        );
    }

    private void scrollTo(AudioListEntry entry) {
        try {
            double height = spAudioList.getContent().getBoundsInLocal().getHeight();
            double y = entry.getBoundsInParent().getMaxY();

            spAudioList.setVvalue(y / height);
            entry.requestFocus();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void onMultipleSelectionStared() {
        Platform.runLater(() -> {
            ViewManager viewManager = ViewManager.getInstance();
            Parent layoutFromView = viewManager.createLayoutFromView(Views.MULTI_SELECTION_LAYOUT_VIEW);
            MultiselectionLayoutViewController viewController = viewManager.getViewController(Views.MULTI_SELECTION_LAYOUT_VIEW);
            viewController.setClearCoverText(LanguageUtil.getString("remove.all.cover.from.selected.audio.files"));

            multiSelectionLayout.getChildren().clear();
            multiSelectionLayout.getChildren().add(layoutFromView);
            multiSelectionLayout.setVisible(true);

            AnimationHelper.slide(multiSelectionLayout, 0, 1, 150, 5);
        });
    }

    @Override
    public void onMultipleSelectionEnded() {
        AnimationHelper.slide(multiSelectionLayout, 1, 0, 5, 150);
    }

    @Override
    public void onParentListEntryDeleted(String key) {
        List<FolderListEntry> entriesToRemove = lwAudioList.getChildren()
                .stream()
                .filter(x -> x instanceof FolderListEntry)
                .map(x -> ((FolderListEntry) x))
                .filter(x -> x.getPath().equalsIgnoreCase(key))
                .collect(Collectors.toList());

        lwAudioList.getChildren().removeAll(entriesToRemove);
    }

    public void setAudioLineBusy(SimpleAudioWrapper simpleAudioWrapper, boolean isBusy) {
        getAudioListEntry(simpleAudioWrapper).setBusy(isBusy);
    }

    public void setEntryToProcessingState(AudioWrapper audioWrapper, SimpleAudioWrapper simpleAudioWrapper) {
        showAudioInfo(audioWrapper);
        highlightInList(simpleAudioWrapper);
        setAudioLineBusy(simpleAudioWrapper, true);
    }

    public void setEntryToFinishedState(SimpleAudioWrapper simpleAudioWrapper) {
        unHighlightInList(simpleAudioWrapper);
        setAudioLineBusy(simpleAudioWrapper, false);
        increaseProgress();
    }
}
