package de.itlobby.discoverj.searchservice;

import de.itlobby.discoverj.models.AudioWrapper;
import de.itlobby.discoverj.models.LocalMatchInfo;
import de.itlobby.discoverj.settings.AppConfig;
import de.itlobby.discoverj.settings.Settings;
import de.itlobby.discoverj.util.AudioUtil;
import de.itlobby.discoverj.util.ImageUtil;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static de.itlobby.discoverj.util.AudioUtil.*;

public class LocalService implements SearchService {
    private static final Map<String, List<LocalMatchInfo>> scanInfoCache = new HashMap<>();
    private final Logger log = LogManager.getLogger(this.getClass());

    @Override
    public List<BufferedImage> searchCover(AudioWrapper audioWrapper) {
        AudioFile audioFile = audioWrapper.getAudioFile();
        AppConfig config = Settings.getInstance().getConfig();

        if (new File(config.getLocalAdditionalFolderPath()).exists()) {
            return getCoverFromCustomFolder(audioWrapper);
        }

        File currentAudioFile = audioFile.getFile();
        List<BufferedImage> coverImagesFromSameFolder = getCoverFromAudioFolder(currentAudioFile);
        if (config.isLocalScanAudioFiles()) {
            coverImagesFromSameFolder.addAll(getCoverImagesFromOtherAudioFiles(audioFile, currentAudioFile));
        }

        return coverImagesFromSameFolder;
    }

    private List<BufferedImage> getCoverImagesFromOtherAudioFiles(AudioFile audioFile, File currentAudio) {
        AppConfig config = Settings.getInstance().getConfig();
        File parentFile = currentAudio.getParentFile();
        String parentFilePath = parentFile.getAbsolutePath();
        LocalMatchInfo currentMatchInfos = LocalMatchInfo.of(
                currentAudio.getAbsolutePath(),
                getAlbum(audioFile),
                getAlbumArtist(audioFile),
                getYear(audioFile),
                haveCover(audioFile)
        );

        long s = System.currentTimeMillis();
        fillLocalScannerCache(
                parentFile,
                audioFile
        );
        log.info("Built cache in: {}ms", System.currentTimeMillis() - s);

        if (!scanInfoCache.containsKey(parentFilePath)) {
            return Collections.emptyList();
        }

        s = System.currentTimeMillis();
        List<BufferedImage> bla = scanInfoCache.get(parentFilePath)
                .parallelStream()
                .filter(LocalMatchInfo::isHaveCover)
                .filter(otherFile -> matchesCriteria(currentMatchInfos, otherFile, config))
                .map(otherFile -> AudioUtil.getAudioFile(otherFile.getPath()))
                .flatMap(Optional::stream)
                .map(AudioUtil::getCoverAsBufImg)
                .flatMap(Optional::stream)
                .filter(SearchService::reachesMinRequiredCoverSize)
                .collect(Collectors.toList());
        log.info("Matched in: {}ms", System.currentTimeMillis() - s);
        return bla;
    }

    private boolean matchesCriteria(LocalMatchInfo currentFile, LocalMatchInfo otherFile, AppConfig config) {
        boolean matches = !config.isLocalMatchAlbum() || currentFile.getAlbum().equals(otherFile.getAlbum());

        if (config.isLocalMatchAlbumArtist() && !currentFile.getAlbumArtist().equals(otherFile.getAlbumArtist())) {
            matches = false;
        }

        if (config.isLocalMatchYear() && !currentFile.getYear().equals(otherFile.getYear())) {
            matches = false;
        }

        return matches;
    }

    private List<BufferedImage> getCoverFromCustomFolder(AudioWrapper audioWrapper) {
        AppConfig config = Settings.getInstance().getConfig();
        File customDir = new File(config.getLocalAdditionalFolderPath());
        String pattern = config.getLocalNamePattern();

        AudioFile audioFile = audioWrapper.getAudioFile();
        String coverFileName = pattern
                .replace("%filename%", audioWrapper.getFileName())
                .replace("%artist%", AudioUtil.getArtist(audioFile))
                .replace("%title%", AudioUtil.getTitle(audioFile))
                .replace("%album%", getAlbum(audioFile))
                .replace("%dummy%", "");

        return FileUtils.listFiles(customDir, VALID_IMAGE_FILE_EXTENSION, false).stream()
                .filter(imageFile -> imageFile.getName().toLowerCase().contains(coverFileName.toLowerCase()))
                .parallel()
                .map(ImageUtil::getMaybeImage)
                .flatMap(Optional::stream)
                .filter(SearchService::reachesMinRequiredCoverSize)
                .sorted(imageSizeComparator()) // biggest image first
                .collect(Collectors.toList());
    }

    private List<BufferedImage> getCoverFromAudioFolder(File currentAudioFile) {
        File parentDir = new File(currentAudioFile.getParent());
        return FileUtils.listFiles(parentDir, VALID_IMAGE_FILE_EXTENSION, false).stream()
                .parallel()
                .map(ImageUtil::getMaybeImage)
                .flatMap(Optional::stream)
                .filter(SearchService::reachesMinRequiredCoverSize)
                .sorted(imageSizeComparator()) // biggest image first
                .collect(Collectors.toList());
    }

    private Comparator<BufferedImage> imageSizeComparator() {
        return (x, y) -> Integer.compare(x.getHeight() * x.getWidth(), y.getHeight() * y.getWidth()) * -1;
    }

    public void fillLocalScannerCache(File parent, AudioFile audioFile) {
        if (scanInfoCache.containsKey(parent.getAbsolutePath())) {
            return;
        }

        List<LocalMatchInfo> scanInfoList = FileUtils.listFiles(parent, VALID_AUDIO_FILE_EXTENSION, false)
                .parallelStream()
                .filter(file -> !file.equals(audioFile.getFile()))
                .map(this::buildCriteriaMatcher)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());

        scanInfoCache.put(parent.getAbsolutePath(), scanInfoList);
    }

    private Optional<LocalMatchInfo> buildCriteriaMatcher(File file) {
        try {
            AudioFile audioFile = AudioFileIO.read(file);
            return Optional.of(LocalMatchInfo.of(
                    file.getAbsolutePath(),
                    getAlbum(audioFile),
                    getAlbumArtist(audioFile),
                    getYear(audioFile),
                    haveCover(audioFile)
            ));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return Optional.empty();
    }
}