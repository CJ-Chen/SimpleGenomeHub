package simplegenomehub.population.fst.chart.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 轨道板模型：管理所有轨道
 */
public class TrackBoardModel {
    private final List<TrackModel> tracks;
    private int nextTrackId = 1;
    
    public TrackBoardModel() {
        this.tracks = new ArrayList<>();
    }
    
    public List<TrackModel> getTracks() {
        return new ArrayList<>(tracks);
    }
    
    public TrackModel createTrack() {
        TrackModel track = new TrackModel("track_" + nextTrackId++);
        tracks.add(track);
        return track;
    }
    
    public void addTrack(TrackModel track) {
        if (track != null && !tracks.contains(track)) {
            tracks.add(track);
        }
    }
    
    public void removeTrack(String trackId) {
        tracks.removeIf(t -> t.getId().equals(trackId));
    }
    
    public void moveTrack(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= tracks.size() ||
            toIndex < 0 || toIndex >= tracks.size() ||
            fromIndex == toIndex) {
            return;
        }
        TrackModel track = tracks.remove(fromIndex);
        tracks.add(toIndex, track);
    }
    
    public TrackModel getTrack(String trackId) {
        for (TrackModel track : tracks) {
            if (track.getId().equals(trackId)) {
                return track;
            }
        }
        return null;
    }
    
    public int getTrackIndex(String trackId) {
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i).getId().equals(trackId)) {
                return i;
            }
        }
        return -1;
    }
}
