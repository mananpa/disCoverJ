package de.itlobby.discoverj.models;

import de.itlobby.discoverj.searchservice.DeezerService;
import de.itlobby.discoverj.searchservice.DiscogsService;
import de.itlobby.discoverj.searchservice.ItunesService;
import de.itlobby.discoverj.searchservice.LocalService;
import de.itlobby.discoverj.searchservice.MusicbrainzService;
import de.itlobby.discoverj.searchservice.SearchService;
import de.itlobby.discoverj.searchservice.SearxService;
import de.itlobby.discoverj.searchservice.SpotifyService;

import java.io.Serializable;
import java.util.Arrays;

public enum SearchEngineType implements Serializable {
    ITUNES("iTunes", "logos/itunes.png", ItunesService.class, true),
    DEEZER("Deezer", "logos/deezer.png", DeezerService.class, true),
    SPOTIFY("Spotify", "logos/spotify.png", SpotifyService.class, true),
    DISCOGS("Discogs", "logos/discogs.png", DiscogsService.class, true),
    MUSICBRAINZ("Musicbrainz", "logos/musicbrainz.png", MusicbrainzService.class, true),
    SEARX("searx", "logos/searx.png", SearxService.class, true),
    LOCAL("Local", "logos/local.png", LocalService.class, true);

    private final String name;
    private final String logoPath;
    private final Class<? extends SearchService> serviceClass;
    private final boolean returnsMultipleImages;

    SearchEngineType(String name, String logoPath, Class<? extends SearchService> serviceClass, boolean returnsMultipleImages) {
        this.name = name;
        this.logoPath = logoPath;
        this.serviceClass = serviceClass;
        this.returnsMultipleImages = returnsMultipleImages;
    }

    public static SearchEngineType getByName(String name) {
        return Arrays.stream(values())
                .filter(searchEngineType -> searchEngineType.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public String getName() {
        return name;
    }

    public String getLogoPath() {
        return logoPath;
    }

    public Class<? extends SearchService> getServiceClass() {
        return serviceClass;
    }

    public boolean isReturnsMultipleImages() {
        return returnsMultipleImages;
    }
}
