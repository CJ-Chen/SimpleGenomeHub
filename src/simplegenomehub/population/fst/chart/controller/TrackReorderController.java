package simplegenomehub.population.fst.chart.controller;

import simplegenomehub.population.fst.chart.FSTTrackChartPanel;
import simplegenomehub.population.fst.chart.model.TrackBoardModel;
import simplegenomehub.population.fst.chart.model.TrackModel;
import simplegenomehub.population.fst.chart.view.TrackBoardPanel;
import simplegenomehub.population.fst.chart.view.TrackView;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import javax.swing.SwingUtilities;

/**
 * 轨道重排控制器：处理轨道的拖拽重排、删除、上下边界拖拽改变高度、折叠切换
 */
public class TrackReorderController {
    private static final int EDGE_TOLERANCE = 5;
    private static final int EDGE_NONE = 0;
    private static final int EDGE_TOP = 1;
    private static final int EDGE_BOTTOM = 2;

    private final TrackBoardPanel trackBoardPanel;
    private final TrackBoardModel boardModel;
    private final FSTTrackChartPanel mainPanel;

    private TrackView draggingTrack = null;
    private int dragStartY = -1;
    private int insertIndicatorY = -1;

    // 高度拖拽状态
    private TrackView resizingTrack = null;
    private int resizeEdge = EDGE_NONE;
    private int resizeStartY = 0;
    private int resizeStartHeight = 0;

    public TrackReorderController(TrackBoardPanel trackBoardPanel, TrackBoardModel boardModel,
                                 FSTTrackChartPanel mainPanel) {
        this.trackBoardPanel = trackBoardPanel;
        this.boardModel = boardModel;
        this.mainPanel = mainPanel;

        setupTrackListeners();
    }

    private void setupTrackListeners() {
        trackBoardPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                TrackView trackView = trackBoardPanel.getTrackViewAt(e.getPoint());
                if (trackView == null) return;

                Point trackPoint = SwingUtilities.convertPoint(trackBoardPanel, e.getPoint(), trackView);

                // 1) 折叠按钮（Y轴左上角的小三角）
                if (trackView.getCollapseButtonBounds().contains(trackPoint)) {
                    TrackModel model = trackView.getTrackModel();
                    model.setCollapsed(!model.isCollapsed());
                    trackView.repaint();
                    mainPanel.repaint();
                    return;
                }

                // 2) 上下边界：拖拽改变该大卡牌高度（避免与左侧把手冲突）
                if (!trackView.getHandleBounds().contains(trackPoint)) {
                    int edge = edgeAt(trackView, trackPoint);
                    if (edge != EDGE_NONE) {
                        resizingTrack = trackView;
                        resizeEdge = edge;
                        resizeStartY = e.getY();
                        resizeStartHeight = trackView.getTrackModel().getHeight();
                        return;
                    }
                }

                // 3) 左侧把手：拖拽重排
                if (trackView.getHandleBounds().contains(trackPoint)) {
                    draggingTrack = trackView;
                    dragStartY = e.getY();

                    Point mouseLocation = SwingUtilities.convertPoint(trackBoardPanel, e.getPoint(), mainPanel);
                    Point trackLocation = SwingUtilities.convertPoint(trackView, 0, 0, mainPanel);
                    Point offset = new Point(mouseLocation.x - trackLocation.x, mouseLocation.y - trackLocation.y);

                    mainPanel.setDraggingTrack(trackView, offset);
                    mainPanel.updateDraggingTrackLocation(mouseLocation);
                    mainPanel.repaint();
                }

                // 4) 删除按钮
                if (trackView.getDeleteBounds().contains(trackPoint)) {
                    boardModel.removeTrack(trackView.getTrackModel().getId());
                    trackBoardPanel.rebuildViews(mainPanel);
                    mainPanel.repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (resizingTrack != null) {
                    resizingTrack = null;
                    resizeEdge = EDGE_NONE;
                    trackBoardPanel.setCursor(Cursor.getDefaultCursor());
                    return;
                }

                if (draggingTrack != null) {
                    int targetY = e.getY();
                    int targetIndex = trackBoardPanel.getTrackIndexAtY(targetY);
                    int sourceIndex = boardModel.getTrackIndex(draggingTrack.getTrackModel().getId());

                    mainPanel.clearDraggingTrack();

                    if (targetIndex >= 0 && targetIndex != sourceIndex) {
                        boardModel.moveTrack(sourceIndex, targetIndex);
                        trackBoardPanel.rebuildViews(mainPanel);
                    }

                    draggingTrack = null;
                    dragStartY = -1;
                    insertIndicatorY = -1;
                    mainPanel.repaint();
                }
            }
        });

        trackBoardPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (resizingTrack != null) {
                    int dy = e.getY() - resizeStartY;
                    int newHeight = (resizeEdge == EDGE_BOTTOM) ? resizeStartHeight + dy
                                                                     : resizeStartHeight - dy;
                    resizingTrack.getTrackModel().setHeight(newHeight);
                    resizingTrack.refreshHeight();
                    trackBoardPanel.revalidate();
                    mainPanel.repaint();
                    return;
                }

                if (draggingTrack != null) {
                    int currentY = e.getY();
                    int targetIndex = trackBoardPanel.getTrackIndexAtY(currentY);

                    if (targetIndex >= 0) {
                        TrackView targetTrack = trackBoardPanel.getTrackViews().get(targetIndex);
                        insertIndicatorY = targetTrack.getY();
                    } else {
                        insertIndicatorY = -1;
                    }

                    Point dragLocation = SwingUtilities.convertPoint(trackBoardPanel, e.getPoint(), mainPanel);
                    mainPanel.updateDraggingTrackLocation(dragLocation);
                    mainPanel.repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                TrackView trackView = trackBoardPanel.getTrackViewAt(e.getPoint());
                if (trackView == null) {
                    trackBoardPanel.setCursor(Cursor.getDefaultCursor());
                    return;
                }

                Point trackPoint = SwingUtilities.convertPoint(trackBoardPanel, e.getPoint(), trackView);

                boolean onEdge = !trackView.getHandleBounds().contains(trackPoint)
                        && edgeAt(trackView, trackPoint) != EDGE_NONE;
                trackBoardPanel.setCursor(onEdge
                        ? Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
                        : Cursor.getDefaultCursor());

                // 更新把手和删除按钮的悬停状态
                trackView.setHandleHovered(trackView.getHandleBounds().contains(trackPoint));
                trackView.setDeleteHovered(trackView.getDeleteBounds().contains(trackPoint));
                trackView.repaint();
            }
        });
    }

    /** 判断点在轨道上边界/下边界 */
    private int edgeAt(TrackView trackView, Point p) {
        int h = trackView.getHeight();
        if (h <= 0) return EDGE_NONE;
        if (p.y <= EDGE_TOLERANCE) return EDGE_TOP;
        if (p.y >= h - EDGE_TOLERANCE) return EDGE_BOTTOM;
        return EDGE_NONE;
    }
}
