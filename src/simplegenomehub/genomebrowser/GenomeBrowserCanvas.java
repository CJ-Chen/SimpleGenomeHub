package simplegenomehub.genomebrowser;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Custom-drawn single-chromosome browser. Rendering follows PlastYes-Kuai's
 * Correct Annotation canvas (palette, six amino-acid frames, wrap layout);
 * data is pulled lazily from {@link GenomeBrowserData}.
 */
public final class GenomeBrowserCanvas extends JPanel {

    public static final int LEFT = 68;
    public static final int RIGHT = 10;

    private final BrowserViewState state;
    private final GenomeBrowserData data;

    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final JPanel linear = new JPanel(new BorderLayout());
    private final JPanel sequenceRow = new JPanel(new BorderLayout());
    private final SequenceTrack sequenceTrack = new SequenceTrack();
    private final GeneTrack geneTrack = new GeneTrack();
    private final JScrollPane geneScroll = new JScrollPane(geneTrack);
    private final WrappedTrack wrappedTrack = new WrappedTrack();
    private final JScrollPane wrappedScroll = new JScrollPane(wrappedTrack);
    /** 折行卡片：右边挂一条基因区（lane 窗口）滚动条，仅在基因超出固定高度时出现。 */
    private final JPanel wrappedCard = new JPanel(new BorderLayout());
    private final JScrollBar wrappedLaneBar = new JScrollBar(JScrollBar.VERTICAL);
    private final Navigator navigator = new Navigator();

    private boolean wrapped;
    private boolean showAminoAcids;
    private List<AnnotationIndex.Locus> visibleLoci = Collections.emptyList();
    private int laneCount = 1;
    /** 折行视图行高用的“整条染色体最大 lane 数”缓存。 */
    private String laneCountChromosome;
    private int laneCountCache = 1;
    /** 上一次真正设置到基因轨道上的内容高度，用来发现 lane 数变化后的重新布局。 */
    private int appliedGeneHeight = -1;
    /** 进入折行视图后想落到哪一行；视口定型前一直挂着，由 resize 回调补上。 */
    private int pendingWrappedRow = -1;
    /** 防重入：applyPendingWrappedRow 里的 validate() 会同步触发 viewport 的 change 回调。 */
    private boolean applyingWrappedRow;
    /** 折行视图基因区滚动条的位置（所有行一起平移的 lane 窗口起点）。 */
    private int wrappedLaneOffset;
    private AnnotationIndex.Locus selectedLocus;
    /** 选中基因所有外显子的并集（相邻/重叠已合并），点选时算一次。 */
    private List<AnnotationIndex.Part> selectedExons = Collections.emptyList();
    /** 选中基因各 CDS 转录本的片段→氨基酸行相位，按转录本缓存。 */
    private final Map<AnnotationIndex.Transcript, List<CdsSegment>> cdsSegmentCache = new HashMap<>();
    private final Map<AnnotationIndex.Transcript, Integer> translationFrameCache = new HashMap<>();
    private String highlightChromosome;
    private long highlightStart;
    private long highlightEnd;

    private final List<GeneHandle> geneHandles = new ArrayList<>();
    private final List<long[]> selections = new ArrayList<>();
    private long[] selectionDrag;
    private Long dragAnchorCoordinate;
    private Point panPress;
    private double panStart;
    private int panLastY;
    private boolean panning;

    private LongConsumer coordinateListener;
    private Runnable changeListener;
    /** 右键菜单由面板接管；没装钩子时退回画布自带的复制菜单。 */
    private java.util.function.Consumer<MouseEvent> contextMenuHandler;

    public GenomeBrowserCanvas(BrowserViewState state, GenomeBrowserData data) {
        super(new BorderLayout());
        this.state = state;
        this.data = data;

        geneScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        geneScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        geneScroll.setBorder(null);
        geneScroll.getVerticalScrollBar().setUnitIncrement(24);

        sequenceRow.add(sequenceTrack, BorderLayout.CENTER);
        JPanel margin = new JPanel();
        margin.setPreferredSize(new Dimension(geneScroll.getVerticalScrollBar().getPreferredSize().width, 0));
        sequenceRow.add(margin, BorderLayout.EAST);

        linear.add(sequenceRow, BorderLayout.NORTH);
        linear.add(geneScroll, BorderLayout.CENTER);
        linear.add(navigator, BorderLayout.SOUTH);

        wrappedScroll.setBorder(null);
        wrappedScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        wrappedScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        wrappedScroll.getVerticalScrollBar().setUnitIncrement(32);
        wrappedScroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        wrappedScroll.getViewport().addChangeListener(e -> {
            // 视口尺寸/内容尺寸变化后滚动条模型才更新，正好在这里补做未完成的滚动
            applyPendingWrappedRow();
            wrappedTrack.repaint();
        });
        // 宽度一变，每行能放的碱基数就变，总行数也随之变，必须重算内容尺寸
        wrappedScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                if (!wrapped) return;
                updateWrappedLaneBar();
                updateWrappedLayout();
                applyPendingWrappedRow();
            }
        });

        wrappedLaneBar.setUnitIncrement(1);
        wrappedLaneBar.setBlockIncrement(WRAPPED_VISIBLE_LANES);
        wrappedLaneBar.setToolTipText("Scroll the gene lanes (only shown when genes need more lanes)");
        wrappedLaneBar.setVisible(false);
        wrappedLaneBar.addAdjustmentListener(e -> {
            if (e.getValue() == wrappedLaneOffset) return;
            wrappedLaneOffset = Math.max(0, e.getValue());
            wrappedTrack.repaint();
        });
        // 指针停在基因区滚动条上时，滚轮也用来平移 lane 窗口
        wrappedLaneBar.addMouseWheelListener(e -> {
            int last = Math.max(0, wrappedLaneBar.getMaximum() - WRAPPED_VISIBLE_LANES);
            int step = e.getWheelRotation() > 0 ? 1 : -1;
            wrappedLaneBar.setValue(Math.max(0, Math.min(last, wrappedLaneBar.getValue() + step)));
            e.consume();
        });
        wrappedCard.add(wrappedScroll, BorderLayout.CENTER);
        wrappedCard.add(wrappedLaneBar, BorderLayout.EAST);

        body.add(linear, "linear");
        body.add(wrappedCard, "wrapped");
        add(body, BorderLayout.CENTER);

        installSelectionKeys();
        state.addListener(() -> {
            updateVisibility();
            if (changeListener != null) changeListener.run();
        });
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                state.normalize(viewWidth());
                updateVisibility();
                if (changeListener != null) changeListener.run();
            }
        });
        SwingUtilities.invokeLater(this::updateVisibility);
    }

    // ---------------------------------------------------------------- public API

    public void setCoordinateListener(LongConsumer listener) { this.coordinateListener = listener; }
    public void setChangeListener(Runnable listener) { this.changeListener = listener; }
    public void setContextMenuHandler(java.util.function.Consumer<MouseEvent> handler) {
        this.contextMenuHandler = handler;
    }

    /** 把右键交给面板；面板负责弹菜单。 */
    private void requestContextMenu(MouseEvent event) {
        if (contextMenuHandler != null) {
            contextMenuHandler.accept(event);
        } else {
            showSelectionMenu(event);
        }
    }

    /** 视图坐标（线性：直接算；折行：行号 + 行内偏移）。 */
    private double coordinateAtPoint(Point point) {
        if (point == null || state.length() == 0) return -1;
        if (wrapped) {
            return Math.floor(rowAt(point.y)) * wrappedBasesPerRow()
                + Math.max(0, point.x - LEFT) * state.basesPerPixel();
        }
        return state.start() + Math.max(0, point.x - LEFT) * state.basesPerPixel();
    }

    public boolean isWrapped() { return wrapped; }

    /**
     * 折行视图里当前第一行的基因组范围（1-based，闭区间）；线性视图返回 null。
     * （折行时可见内容跨多行、并不连续，所以状态栏只报第一行。）
     */
    public long[] wrappedFirstRowRange() {
        if (!wrapped || state.length() == 0) return null;
        int bases = wrappedBasesPerRow();
        long from = (long) wrappedFirstVisibleRow() * bases + 1;
        if (from > state.length()) return null;
        long to = Math.min(state.length(), from + bases - 1);
        return new long[]{from, to};
    }

    /**
     * 切换折行 / 线性视图。
     * 两种视图的“当前区域”要互相接得上：进折行时跳到包含当前起点的行，
     * 回线性时把该行的起点当成新的视图起点。
     */
    public void setWrapped(boolean value) {
        if (wrapped == value) return;
        double resumeStart = state.start();
        if (wrapped) {
            // 离开折行：把当前第一行换算成基因组坐标
            resumeStart = Math.min(state.length(), wrappedFirstVisibleRow() * (double) wrappedBasesPerRow());
        }
        wrapped = value;
        cards.show(body, value ? "wrapped" : "linear");
        if (!value) {
            pendingWrappedRow = -1;
            state.setNormalizedStart(resumeStart, viewWidth());
        }
        // 进折行时故意不动 basesPerPixel：折行用的是线性视图当时的缩放，
        // 只是把同样的碱基/像素铺成多行。
        revalidate();
        updateVisibility();
        if (value) {
            // 布局要等 Swing 走完一轮才知道视口宽度，这里再落一次滚动位置
            pendingWrappedRow = wrappedRowFor(state.start());
            // revalidate() 安排的布局要等下一轮事件才执行，再套一层 invokeLater 才轮得到我们
            SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
                applyPendingWrappedRow();
                wrappedTrack.repaint();
            }));
        }
        repaint();
    }

    public void fit() {
        state.fit(viewWidth());
        updateVisibility();
        if (wrapped) pendingWrappedRow = 0;
        applyPendingWrappedRow();
        repaint();
    }

    public void jumpTo(long position) {
        if (wrapped) {
            // 折行视图沿用线性时的缩放，跳转只表示“滚到那一行”
            scrollToWrappedRow(wrappedRowFor(Math.max(0, position - 1)));
            repaint();
            return;
        }
        state.jump(position, viewWidth());
        updateVisibility();
        repaint();
    }

    public void showRange(long from, long to) {
        if (wrapped) {
            // 同上：折行时不为了塞下某个区间而改缩放
            scrollToWrappedRow(wrappedRowFor(Math.max(0, Math.min(from, to) - 1)));
            repaint();
            return;
        }
        state.showRange(from, to, viewWidth());
        updateVisibility();
        repaint();
    }

    public void showChromosome(String name) {
        state.setChromosome(name, data.length(name), viewWidth());
        // 换染色体时选中和框选都必须丢掉，否则会拿着旧坐标在新染色体上画框
        setSelectedLocus(null);
        selections.clear();
        selectionDrag = null;
        dragAnchorCoordinate = null;
        wrappedLaneOffset = 0;
        geneScroll.getViewport().setViewPosition(new Point(0, 0));
        updateVisibility();
        if (wrapped) pendingWrappedRow = 0;
        applyPendingWrappedRow();
        repaint();
    }

    public void selectLocus(AnnotationIndex.Locus locus) {
        setSelectedLocus(locus);
        if (locus != null) jumpTo(locus.strand == '-' ? Math.max(0, locus.end - 1) : locus.start);
        repaint();
    }

    /**
     * 唯一的选中入口：更新选中基因、预计算外显子并集，并让整块画布重绘
     * （序列区要画黑色外显子框和氨基酸行灰块，基因轨道要画红框，只重绘某个轨道不够）。
     */
    private void setSelectedLocus(AnnotationIndex.Locus locus) {
        if (locus != null) {
            // 手动选中新基因时，之前拖拽框选的框随之消失（与 PlastYes 的 pick() 一致）
            selections.clear();
            selectionDrag = null;
            dragAnchorCoordinate = null;
        }
        selectedLocus = locus;
        selectedExons = mergeExons(locus);
        repaint();
    }

    /** 一个基因所有转录本外显子的并集，重叠或相邻的合成一段。 */
    private static List<AnnotationIndex.Part> mergeExons(AnnotationIndex.Locus locus) {
        if (locus == null) return Collections.emptyList();
        List<AnnotationIndex.Part> parts = new ArrayList<>();
        for (AnnotationIndex.Transcript transcript : locus.transcripts) parts.addAll(transcript.exons);
        if (parts.isEmpty()) return Collections.emptyList();
        parts.sort((a, b) -> Integer.compare(a.start, b.start));
        List<AnnotationIndex.Part> merged = new ArrayList<>();
        int start = parts.get(0).start;
        int end = parts.get(0).end;
        for (int i = 1; i < parts.size(); i++) {
            AnnotationIndex.Part part = parts.get(i);
            if (part.start <= end) {
                end = Math.max(end, part.end);
                continue;
            }
            merged.add(new AnnotationIndex.Part(start, end));
            start = part.start;
            end = part.end;
        }
        merged.add(new AnnotationIndex.Part(start, end));
        return merged;
    }

    // ------------------------------------------------------- 选中 CDS 的阅读相位

    /** 一个 CDS 片段落在氨基酸行上的位置：起始/结束（已跳过 frame）与相位（=行号）。 */
    private static final class CdsSegment {
        final int start;
        final int end;
        final int phase;

        CdsSegment(int start, int end, int phase) {
            this.start = start;
            this.end = end;
            this.phase = phase;
        }
    }

    /**
     * CDS 片段按**转录顺序**排列：正链升序，负链降序。
     * 参考项目里 GenBank 的 complement(join(...)) 会被解析成降序，相位累加就靠这个顺序，
     * 用错顺序相位全错。
     */
    private static List<AnnotationIndex.Part> transcriptOrderCds(AnnotationIndex.Transcript transcript) {
        List<AnnotationIndex.Part> parts = new ArrayList<>(transcript.cds);
        parts.sort((a, b) -> Integer.compare(a.start, b.start));
        if (transcript.strand == '-') Collections.reverse(parts);
        return parts;
    }

    /** 剪接之后的 CDS 序列（转录方向，负链取互补），只在需要推算起始帧时读一次。 */
    private String splicedCds(AnnotationIndex.Transcript transcript, List<AnnotationIndex.Part> parts) {
        String chromosome = state.chromosome();
        StringBuilder bases = new StringBuilder();
        for (AnnotationIndex.Part part : parts) {
            if (transcript.strand == '-') {
                for (int p = part.end - 1; p >= part.start; p--) bases.append(complement(data.baseAt(chromosome, p)));
            } else {
                for (int p = part.start; p < part.end; p++) bases.append(data.baseAt(chromosome, p));
            }
        }
        return bases.toString();
    }

    /**
     * 起始阅读帧 0..2：优先 GFF3 的 codon_start；
     * 否则首角码是起始密码子就算 0；再否则取"终止子最少"的帧（与参考项目 frame() 一致）。
     */
    private int translationFrame(AnnotationIndex.Transcript transcript) {
        Integer cached = translationFrameCache.get(transcript);
        if (cached != null) return cached;
        int frame = transcript.codonStart;
        if (frame < 0) {
            String bases = splicedCds(transcript, transcriptOrderCds(transcript));
            int table = state.translationTable();
            if (bases.length() >= 3 && CodonTable.isStart(table, bases.substring(0, 3))) {
                frame = 0;
            } else {
                int best = 0;
                int fewest = Integer.MAX_VALUE;
                for (int f = 0; f < 3; f++) {
                    String amino = CodonTable.translate(table, bases.substring(Math.min(f, bases.length())));
                    int stops = 0;
                    for (int i = 0; i + 1 < amino.length(); i++) if (amino.charAt(i) == '*') stops++;
                    if (stops < fewest) {
                        fewest = stops;
                        best = f;
                    }
                }
                frame = best;
            }
        }
        translationFrameCache.put(transcript, frame);
        return frame;
    }

    /**
     * 把 CDS 片段映射到氨基酸行。相位用"剪接后的累计长度"推进，绝不能用基因组距离：
     * <pre>
     *   skip  = max(0, frame - offset)                                 // 只对第一段有效
     *   phase = floorMod('+' ? part.start - (offset - frame)
     *                        : part.end   - 3 + (offset - frame), 3)
     *   offset += part.length()
     * </pre>
     * phase 的含义是"该片段里密码子最左碱基的 mod 3"，正好就是 drawCodons 的行号。
     */
    private List<CdsSegment> cdsSegments(AnnotationIndex.Transcript transcript) {
        List<CdsSegment> cached = cdsSegmentCache.get(transcript);
        if (cached != null) return cached;
        List<CdsSegment> segments = new ArrayList<>();
        if (!transcript.cds.isEmpty()) {
            int frame = translationFrame(transcript);
            int offset = 0;
            for (AnnotationIndex.Part part : transcriptOrderCds(transcript)) {
                int skip = Math.max(0, frame - offset);
                int phase = Math.floorMod(transcript.strand == '-'
                    ? part.end - 3 + (offset - frame)
                    : part.start - (offset - frame), 3);
                int start = transcript.strand == '-' ? part.start : part.start + skip;
                int end = transcript.strand == '-' ? part.end - skip : part.end;
                if (start < end) segments.add(new CdsSegment(start, end, phase));
                offset += part.length();
            }
        }
        cdsSegmentCache.put(transcript, segments);
        return segments;
    }

    /** True when at least one sequence range is selected - what copy and BLAST work on. */
    public boolean hasSelection() {
        return !selections.isEmpty();
    }

    /** The gene that is currently picked, or null when only a sequence range is selected. */
    public AnnotationIndex.Locus selectedLocus() {
        return selectedLocus;
    }

    /** The selected ranges as {@code chr:start-end} (1-based inclusive, comma separated). */
    public String selectionLabel() {
        String chromosome = state == null ? null : state.chromosome();
        if (chromosome == null || selections.isEmpty()) {
            return "";
        }
        List<long[]> ordered = new ArrayList<>(selections);
        ordered.sort((a, b) -> Long.compare(a[0], b[0]));
        StringBuilder label = new StringBuilder();
        for (long[] range : ordered) {
            if (label.length() > 0) {
                label.append(',');
            }
            label.append(chromosome).append(':').append(range[0] + 1).append('-').append(range[1]);
        }
        return label.toString();
    }

    /**
     * Spliced sequence of the picked gene in transcription direction (the BLAST query of a gene).
     * Empty when no gene is picked.
     */
    public String geneSequence() {
        if (selectedLocus == null || data == null) {
            return "";
        }
        AnnotationIndex.Transcript transcript = geneQueryTranscript();
        StringBuilder builder = new StringBuilder();
        for (AnnotationIndex.Part part : geneQueryParts(transcript)) {
            builder.append(data.read(selectedLocus.chromosome, part.start, part.end));
        }
        String sequence = builder.toString();
        char strand = transcript == null ? selectedLocus.strand : transcript.strand;
        return strand == '-' ? reverseComplement(sequence) : sequence;
    }

    /** {@code geneId chr:start-end} (1-based inclusive) of the picked gene; empty without a gene. */
    public String geneLabel() {
        if (selectedLocus == null) {
            return "";
        }
        return selectedLocus.id + " " + selectedLocus.chromosome + ":"
            + (selectedLocus.start + 1) + "-" + selectedLocus.end;
    }

    /** The transcript a gene query is built from: the first with exons, else the first with CDS. */
    private AnnotationIndex.Transcript geneQueryTranscript() {
        for (AnnotationIndex.Transcript transcript : selectedLocus.transcripts) {
            if (!transcript.exons.isEmpty()) {
                return transcript;
            }
        }
        for (AnnotationIndex.Transcript transcript : selectedLocus.transcripts) {
            if (!transcript.cds.isEmpty()) {
                return transcript;
            }
        }
        return selectedLocus.transcripts.isEmpty() ? null : selectedLocus.transcripts.get(0);
    }

    /** Exons of that transcript, its CDS, or the plain gene span when the annotation has neither. */
    private List<AnnotationIndex.Part> geneQueryParts(AnnotationIndex.Transcript transcript) {
        if (transcript != null && !transcript.exons.isEmpty()) {
            return transcript.exons;
        }
        if (transcript != null && !transcript.cds.isEmpty()) {
            return transcript.cds;
        }
        return java.util.Collections.singletonList(new AnnotationIndex.Part(
            selectedLocus.start, Math.max(selectedLocus.start + 1, selectedLocus.end)));
    }

    public String selectedValue(boolean reverse, boolean amino) {
        String chromosome = state.chromosome();
        if (chromosome == null || selections.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        List<long[]> ordered = new ArrayList<>(selections);
        ordered.sort((a, b) -> Long.compare(a[0], b[0]));
        for (long[] range : ordered) builder.append(data.read(chromosome, range[0], range[1]));
        String value = builder.toString();
        if (reverse) value = reverseComplement(value);
        if (amino) value = CodonTable.translate(state.translationTable(), value);
        return value;
    }

    // ---------------------------------------------------------------- layout

    /**
     * 当前卡片（线性 / 折行）滚动视口的像素宽度。
     * 两个卡片尺寸相同，但各自都有常显的纵向滚动条，所以要按当前卡片取，避免用错宽度。
     */
    private int contentWidth() {
        JScrollPane active = wrapped ? wrappedScroll : geneScroll;
        JScrollPane other = wrapped ? geneScroll : wrappedScroll;
        int width = active.getViewport().getExtentSize().width;
        if (width <= 0) width = other.getViewport().getExtentSize().width;
        if (width <= 0) {
            width = Math.max(200, getWidth() - active.getVerticalScrollBar().getPreferredSize().width);
        }
        return Math.max(1, width);
    }

    private int viewWidth() {
        return Math.max(1, contentWidth() - LEFT - RIGHT);
    }

    private Font uiFont() {
        Font font = getFont();
        return font == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 12) : font;
    }

    private int unit() { return Math.max(18, getFontMetrics(uiFont()).getHeight() + 5); }
    private int cellHeight() { return Math.max(10, unit() - 2); }
    private int cellY(int rowTop) { return rowTop + (unit() - cellHeight()) / 2; }
    /** 外显子条带高度。 */
    private static final int EXON_HEIGHT = 16;
    /** CDS 框上下各比外显子条带多出来的像素。 */
    private static final int CDS_OVERHANG = 4;
    /** 基因名基线到条带顶部的距离。 */
    private static final int LABEL_GAP = 4;
    /** 相邻 lane 之间的空隙。 */
    private static final int LANE_GAP = 10;
    /** 折行视图相邻两行之间的空隙。 */
    private static final int ROW_GAP = 8;

    /** 每个 lane 的高度：基因名一行 + 外显子条带 + CDS 上下外扩 + 行间距。 */
    private int laneHeight() {
        return getFontMetrics(uiFont()).getHeight() + EXON_HEIGHT + CDS_OVERHANG * 2 + LANE_GAP;
    }

    /** 第一条 lane 上方要留出基因名 ascent 的空间，否则第一行基因名会被裁掉。 */
    private int geneTopPad() {
        return getFontMetrics(uiFont()).getAscent() + LABEL_GAP + 6;
    }

    /** 某条 lane 中基因条带（外显子上边缘）的 y 坐标。 */
    private int laneTop(int lane) {
        return geneTopPad() + Math.max(0, lane) * laneHeight();
    }

    /** 给定 lane 数时基因区需要的高度：覆盖所有 lane、最后一个 CDS 框以及底部间隙。 */
    private int geneAreaHeight(int lanes) {
        return laneTop(Math.max(1, lanes) - 1) + EXON_HEIGHT + CDS_OVERHANG + LANE_GAP;
    }

    /** 线性视图里基因轨道需要的内容高度。 */
    private int geneContentHeight() {
        return geneAreaHeight(Math.max(1, laneCount));
    }

    /** 折行视图里每行固定显示几条 lane（超出的部分用基因区滚动条平移查看）。 */
    private static final int WRAPPED_VISIBLE_LANES = 3;

    /**
     * 折行视图里每一行的基因区高度：固定值，与数据无关。
     * 这样行高恒定、滚动平稳，也不会因为整条染色体某处基因密集就把所有行撑高。
     */
    private int wrappedGeneAreaHeight() {
        return geneAreaHeight(WRAPPED_VISIBLE_LANES);
    }

    /** 折行时基因区滚动条需要的最大 lane 数（按染色体缓存一次）。 */
    private int wrappedLaneCount() {
        return Math.max(WRAPPED_VISIBLE_LANES, maxLanesOnChromosome());
    }

    /** 基因在基因区里的 y；折行时按 lane 窗口平移，窗口外的返回 -1 表示跳过。 */
    private int geneLaneY(AnnotationIndex.Locus locus) {
        int lane = locus.lane;
        if (wrapped) {
            lane -= wrappedLaneOffset;
            if (lane < 0 || lane >= WRAPPED_VISIBLE_LANES) return -1;
        }
        return laneTop(lane);
    }

    private int maxLanesOnChromosome() {
        if (state.length() <= 0) return 1;
        String chromosome = state.chromosome();
        if (chromosome == null) return 1;
        if (chromosome.equals(laneCountChromosome)) return laneCountCache;
        laneCountCache = assignLanes(data.annotation().loci(chromosome, 0, state.length()));
        laneCountChromosome = chromosome;
        return laneCountCache;
    }

    private boolean showStrand(boolean reverse) {
        return state.strandMode() == BrowserViewState.StrandMode.BOTH
            || state.strandMode() == (reverse ? BrowserViewState.StrandMode.REVERSE : BrowserViewState.StrandMode.FORWARD);
    }

    private boolean showAminoAcids() { return showAminoAcids; }

    /** 开关氨基酸行（关闭后序列区变矮，只留下碱基行）。 */
    public void setShowAminoAcids(boolean show) {
        if (showAminoAcids == show) return;
        showAminoAcids = show;
        updateVisibility();
        repaint();
    }

    public boolean isShowAminoAcids() { return showAminoAcids; }

    /** 正向 3 个阅读框占用的行数（关闭氨基酸行时为 0）。 */
    private int forwardAaRows() {
        return showAminoAcids && forwardShown() ? 3 : 0;
    }

    /** 反向 3 个阅读框占用的行数（关闭氨基酸行时为 0）。 */
    private int reverseAaRows() {
        return showAminoAcids && reverseShown() ? 3 : 0;
    }

    /**
     * 纵向行序固定为：
     * 正向氨基酸三行 → 正向碱基行 → 反向碱基行 → 反向氨基酸三行。
     * 也就是正向氨基酸在序列上方、反向氨基酸在序列下方。
     */
    private int aaY(boolean reverse, int phase) {
        int first = reverse
            ? forwardAaRows() + (forwardShown() ? 1 : 0) + (reverseShown() ? 1 : 0)
            : 0;
        return 30 + (first + phase) * unit();
    }

    /**
     * 碱基行的行号。双链模式且显示氨基酸时，两链的碱基行之间要留一行空隙，
     * 否则 3'&lt;-5' 与 5'-&gt;3' 两行会挤在一起。
     */
    private int baseY(boolean reverse) {
        int row = reverse ? forwardAaRows() + (forwardShown() ? 1 : 0) : forwardAaRows();
        return 30 + row * unit();
    }

    /** 序列区最后一行的行号（= 需要保留的行数）。 */
    private int sequenceRows() {
        return forwardAaRows() + (forwardShown() ? 1 : 0) + (reverseShown() ? 1 : 0) + reverseAaRows();
    }

    private int sequenceHeight() {
        int rows = Math.max(1, sequenceRows());
        return cellY(30 + (rows - 1) * unit()) + cellHeight() + 1;
    }

    private boolean reverseShown() {
        return state.strandMode() != BrowserViewState.StrandMode.FORWARD;
    }

    private boolean forwardShown() {
        return state.strandMode() != BrowserViewState.StrandMode.REVERSE;
    }

    /**
     * 序列区任何变化（氨基酸行开关、单链/双链切换、字体变化）都要在这里重新计算高度，
     * 并让基因轨道重新分配剩余空间，不要留下空白。
     */
    private void updateVisibility() {
        int sequence = sequenceHeight();
        sequenceTrack.setPreferredSize(new Dimension(100, sequence));
        sequenceTrack.revalidate();
        sequenceRow.revalidate();
        if (wrapped) {
            // 折行视图：内容高度按“行数 × 行高”自己算，线性基因轨道此刻不可见，不用管
            updateWrappedLaneBar();
            updateWrappedLayout();
        } else {
            // 线性视图：基因轨道至少要有内容那么高；父容器剩下的空间也给它，避免下面留白
            int geneContent = geneContentHeight();
            int available = Math.max(geneContent, linear.getHeight() - sequence - 16);
            geneTrack.setPreferredSize(new Dimension(100, available));
            appliedGeneHeight = geneContent;
        }
        geneTrack.revalidate();
        wrappedTrack.revalidate();
        linear.revalidate();
        repaint();
    }

    private final class Slice {
        final double from;
        final double count;
        final int width;
        Slice(double from, double count, int width) {
            this.from = from;
            this.count = Math.max(0, count);
            this.width = width;
        }
        double end() { return from + count; }
        int x(double position) { return LEFT + (int) Math.round((position - from) * state.pixelsPerBase()); }
        int right() { return Math.min(width - RIGHT, x(end())); }
    }

    private Slice slice(double from, double count, int width) {
        int length = state.length();
        double limited = Math.min(count, Math.max(0, length - from));
        return new Slice(from, limited, width);
    }

    // ---------------------------------------------------------------- selection keys

    /** ESC 是唯一的"全部清掉"入口：拖拽框选和选中基因的高亮一起消失。 */
    private void installSelectionKeys() {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "clear-selection");
        getActionMap().put("clear-selection", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                selections.clear();
                selectionDrag = null;
                dragAnchorCoordinate = null;
                setSelectedLocus(null);
                repaint();
            }
        });
    }

    // ---------------------------------------------------------------- tracks

    private final class SequenceTrack extends JPanel {
        SequenceTrack() {
            setBackground(Color.WHITE);
            setOpaque(true);
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) { reportPosition(e.getPoint()); }
                @Override public void mouseExited(MouseEvent e) { reportPosition(null); }
                @Override public void mousePressed(MouseEvent e) {
                    // 只有右键弹菜单；左键一律走框选（点在已有选区里也一样，不弹菜单）。
                    if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) { requestContextMenu(e); return; }
                    if (!SwingUtilities.isLeftMouseButton(e) || state.length() == 0) return;
                    long coordinate = coordinateAt(e.getX());
                    if (coordinate < 0) return;
                    // 左键单击＝取消选择：先清掉旧选区，只有真的拖动了才重新框出一段。
                    dragAnchorCoordinate = coordinate;
                    selectionDrag = null;
                    if (!e.isShiftDown()) selections.clear();
                    repaint();
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (dragAnchorCoordinate == null) return;
                    long coordinate = trim(coordinateAt(e.getX()));
                    if (coordinate == dragAnchorCoordinate) {
                        // 拖回起点就等于没拖：预览框撤掉，松开后不会留下选区。
                        if (selectionDrag != null) { selectionDrag = null; repaint(); }
                        return;
                    }
                    // 真的拖动了才算"新的框选"：此时才让选中基因的高亮让位。
                    if (selectedLocus != null) setSelectedLocus(null);
                    selectionDrag = new long[]{
                        Math.min(dragAnchorCoordinate, coordinate),
                        Math.max(dragAnchorCoordinate, coordinate) + 1
                    };
                    repaint();
                }
                @Override public void mouseReleased(MouseEvent e) {
                    // 右键菜单已经在 mousePressed 里交给面板弹了，这里不再重复弹一个。
                    if (selectionDrag != null) {
                        selections.add(selectionDrag);
                    }
                    selectionDrag = null;
                    dragAnchorCoordinate = null;
                    repaint();
                }
                @Override public void mouseWheelMoved(MouseWheelEvent e) {
                    e.consume();
                    state.zoomAt(e.getX() - LEFT, viewWidth(), e.getWheelRotation());
                    updateVisibility();
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = quality(graphics);
            try {
                Slice slice = slice(state.start(), state.visibleBases(viewWidth()) + 2, getWidth());
                drawRuler(g, slice);
                drawSequenceContent(g, slice);
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * 序列区内容（氨基酸行、碱基行、左侧行标签、底部分隔线）。
     * 线性视图和折行视图的每一行共用同一套画法，避免两边渐渐画得不一致。
     */
    private void drawSequenceContent(Graphics2D g, Slice slice) {
        Graphics2D seq = (Graphics2D) g.create();
        try {
            seq.clipRect(LEFT, 28, Math.max(0, slice.right() - LEFT), sequenceHeight() - 28);
            // 链的顺序固定：先正向（上），后反向（下）
            // 图层顺序照抄 PlastYes：密码子底色 → 选中 CDS 的深灰底 → 灰底上的密码子小格
            // → 碱基行底色/字母 → 拖拽选区黑框；最后再画选中基因的外显子黑框（压在最上）。
            for (boolean reverse : new boolean[]{false, true}) {
                if (!showStrand(reverse)) continue;
                if (showAminoAcids()) {
                    drawCodons(seq, slice, reverse);
                    drawSelectedCdsBand(seq, slice, reverse);
                    drawSelectedCodonCells(seq, slice, reverse);
                }
                int y = baseY(reverse);
                seq.setColor(BrowserPalette.BASE_ROW);
                seq.fillRect(LEFT, cellY(y), Math.max(0, slice.right() - LEFT), cellHeight());
                if (state.pixelsPerBase() >= 7) {
                    for (long p = (long) Math.floor(slice.from); p < Math.ceil(slice.end()); p++) {
                        char base = data.baseAt(state.chromosome(), p);
                        if (reverse) base = complement(base);
                        seq.setColor(BrowserPalette.baseColor(base));
                        centered(seq, String.valueOf(base), slice.x(p + 0.5), y);
                    }
                }
                drawSelectionBoxes(seq, slice, reverse);
            }
            drawSelectedExonFrames(seq, slice);
        } finally {
            seq.dispose();
        }
        g.setFont(uiFont());
        g.setColor(new Color(88, 106, 113));
        for (boolean reverse : new boolean[]{false, true}) {
            if (!showStrand(reverse)) continue;
            if (showAminoAcids()) {
                for (int phase = 0; phase < 3; phase++) {
                    g.drawString((reverse ? "-" : "+") + (phase + 1), 30, aaY(reverse, phase) + unit() - 6);
                }
            }
            g.drawString(reverse ? "3' <- 5'" : "5' -> 3'", 6, baseY(reverse) + unit() - 6);
        }
        g.setColor(BrowserPalette.GRID);
        g.drawLine(0, sequenceHeight() - 1, getWidth(), sequenceHeight() - 1);
    }

    private final class GeneTrack extends JPanel {
        GeneTrack() {
            setBackground(Color.WHITE);
            setOpaque(true);
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) { requestContextMenu(e); return; }
                    if (e.getClickCount() == 1 && activateHandleAt(e.getPoint())) return;
                    AnnotationIndex.Locus hit = locusAt(e.getPoint());
                    // 点空白不清除选中：只有选中新基因 / 新框选 / ESC 才让它消失
                    if (e.getClickCount() == 1 && hit != null) { setSelectedLocus(hit); return; }
                    panPress = e.getPoint();
                    panStart = state.start();
                    panLastY = e.getY();
                    panning = state.length() > 0;
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (!panning) return;
                    state.setNormalizedStart(panStart + (panPress.x - e.getX()) * state.basesPerPixel(), viewWidth());
                    JScrollBar bar = geneScroll.getVerticalScrollBar();
                    bar.setValue(bar.getValue() + (panLastY - e.getY()));
                    panLastY = e.getY();
                    updateVisibility();
                }
                @Override public void mouseReleased(MouseEvent e) { panning = false; setCursor(Cursor.getDefaultCursor()); }
                @Override public void mouseMoved(MouseEvent e) { reportPosition(e.getPoint()); }
                @Override public void mouseExited(MouseEvent e) { reportPosition(null); }
                @Override public void mouseWheelMoved(MouseWheelEvent e) {
                    e.consume();
                    state.zoomAt(e.getX() - LEFT, viewWidth(), e.getWheelRotation());
                    updateVisibility();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = quality(graphics);
            try {
                int width = getWidth();
                Slice slice = slice(state.start(), state.visibleBases(viewWidth()) + 2, width);
                g.setColor(BrowserPalette.MARGIN);
                g.fillRect(0, 0, LEFT - 2, getHeight());
                drawGenes(g, slice, width);
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * 折行视图：整条染色体按“每行 wrappedBasesPerRow() 个碱基”竖向铺开。
     *
     * 这里必须实现 {@link Scrollable}，因为 Swing 默认不会去猜“这个面板要画多长”：
     * <ul>
     *   <li>{@code getScrollableTracksViewportWidth() = true} —— 宽度永远等于视口宽度，
     *       于是 basesPerRow 有唯一确定的值，也不会冒出横向滚动条；</li>
     *   <li>{@code getPreferredScrollableViewportSize()} 返回当前视口大小 —— 若返回内容尺寸，
     *       JScrollPane 的 preferredSize 会等于“总行数 × 行高”，父容器会被撑爆；</li>
     *   <li>内容总高度由 {@link #updateWrappedLayout()} 显式 setPreferredSize 告知，
     *       否则多出来的行永远滚不到。</li>
     * </ul>
     */
    private final class WrappedTrack extends JPanel implements Scrollable {
        WrappedTrack() {
            setBackground(Color.WHITE);
            setOpaque(true);
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) { requestContextMenu(e); return; }
                    if (e.getClickCount() != 1) return;
                    // 点空白同样不清除选中
                    AnnotationIndex.Locus hit = locusAtWrapped(e.getPoint());
                    if (hit != null) setSelectedLocus(hit);
                }
                @Override public void mouseMoved(MouseEvent e) { reportPosition(e.getPoint()); }
                @Override public void mouseExited(MouseEvent e) { reportPosition(null); }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            // 注意：这里故意不注册滚轮监听器。
            // 折行视图不做滚轮缩放；而且 Swing 的 LightweightDispatcher 会把滚轮事件
            // 交给“第一个带滚轮监听器的祖先”，一旦本面板注册了，事件就再也到不了
            // JScrollPane，纵向滚动会失效。
        }

        @Override public Dimension getPreferredScrollableViewportSize() {
            // 关键：绝不能返回内容高度，否则父容器会被“不知道多长”的内容撑开
            Dimension extent = wrappedScroll.getViewport().getExtentSize();
            if (extent.width > 0 && extent.height > 0) return new Dimension(extent);
            return new Dimension(640, 220);
        }

        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            if (orientation == SwingConstants.HORIZONTAL) return 16;
            return Math.max(16, wrappedRowHeight() / 2);
        }

        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            if (orientation == SwingConstants.HORIZONTAL) return 16;
            return Math.max(16, visible.height - wrappedRowHeight());
        }

        @Override public boolean getScrollableTracksViewportWidth() { return true; }

        @Override public boolean getScrollableTracksViewportHeight() { return false; }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (state.length() == 0) return;
            int bases = wrappedBasesPerRow();
            int rowHeight = wrappedRowHeight();
            int rows = wrappedRowCount();
            Rectangle visible = getVisibleRect();
            int firstRow = Math.max(0, visible.y / Math.max(1, rowHeight) - 1);
            int lastRow = Math.min(rows - 1, (visible.y + Math.max(1, visible.height)) / Math.max(1, rowHeight) + 1);
            for (int row = firstRow; row <= lastRow; row++) {
                double from = (double) row * bases;
                double count = Math.min(bases, state.length() - from);
                if (count <= 0) continue;
                Graphics2D g = quality(graphics);
                try {
                    g.translate(0, row * rowHeight);
                    g.clipRect(0, 0, getWidth(), rowHeight);
                    Slice slice = new Slice(from, count, getWidth());
                    drawRowRuler(g, slice, row, bases);
                    drawSequenceContent(g, slice);
                    g.translate(0, sequenceHeight());
                    // 只裁到本行的基因区，别越界画进下一行
                    g.clipRect(0, 0, getWidth(), Math.max(0, rowHeight - sequenceHeight()));
                    drawGenes(g, slice, getWidth());
                } finally {
                    g.dispose();
                }
            }
        }
    }

    private final class Navigator extends JPanel {
        Navigator() {
            setBackground(BrowserPalette.NAVIGATOR);
            setPreferredSize(new Dimension(100, 18));
            MouseAdapter mouse = new MouseAdapter() {
                boolean dragging;
                @Override public void mousePressed(MouseEvent e) {
                    dragging = true;
                    moveTo(e.getX());
                }
                @Override public void mouseDragged(MouseEvent e) { if (dragging) moveTo(e.getX()); }
                @Override public void mouseReleased(MouseEvent e) { dragging = false; }
                private void moveTo(int x) {
                    if (state.length() == 0) return;
                    double fraction = Math.max(0, Math.min(1, x / (double) Math.max(1, getWidth())));
                    state.setNormalizedStart(fraction * state.length() - state.visibleBases(viewWidth()) / 2, viewWidth());
                    updateVisibility();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (state.length() == 0) return;
            Graphics2D g = quality(graphics);
            try {
                int span = Math.max(1, getWidth());
                double thumbStart = state.start() / state.length() * span;
                double thumbSize = Math.min(span, Math.max(12, state.visibleBases(viewWidth()) / state.length() * span));
                g.setColor(BrowserPalette.NAVIGATOR_TRACK);
                g.fillRect(0, getHeight() / 2 - 2, span, 4);
                g.setColor(BrowserPalette.NAVIGATOR_THUMB);
                g.fillRoundRect((int) thumbStart, 3, Math.max(1, (int) thumbSize), getHeight() - 6, 6, 6);
            } finally {
                g.dispose();
            }
        }
    }

    // ---------------------------------------------------------------- drawing

    private void drawRuler(Graphics2D g, Slice slice) {
        drawHighlight(g, slice, 23);
        g.setFont(uiFont());
        FontMetrics fm = g.getFontMetrics();
        g.setColor(BrowserPalette.RULER_TEXT);
        g.drawLine(LEFT, 23, slice.right(), 23);
        long step = niceStep(90 * state.basesPerPixel());
        long first = (long) (Math.floor(slice.from / step) * step);
        List<Rectangle> labels = new ArrayList<>();
        for (long position = first; position <= slice.end() + step; position += step) {
            int x = slice.x(position);
            if (x < LEFT - 1 || x > slice.right() + 40) continue;
            g.setColor(BrowserPalette.RULER_TEXT);
            g.drawLine(x, 19, x, 23);
            String text = formatPosition(position + 1);
            int width = fm.stringWidth(text);
            Rectangle area = new Rectangle(x - width / 2 - 2, 4, width + 4, fm.getHeight());
            boolean overlaps = false;
            for (Rectangle previous : labels) if (previous.intersects(area)) { overlaps = true; break; }
            if (!overlaps) {
                labels.add(area);
                g.setColor(BrowserPalette.TEXT);
                g.drawString(text, x - width / 2, 16);
            }
        }
    }

    private void drawRowRuler(Graphics2D g, Slice slice, int row, int bases) {
        drawHighlight(g, slice, 21);
        g.setFont(uiFont());
        FontMetrics fm = g.getFontMetrics();
        g.setColor(BrowserPalette.GRID);
        g.drawLine(LEFT, 21, slice.right(), 21);
        g.setColor(BrowserPalette.MUTED);
        String text = formatPosition((long) slice.from + 1);
        g.drawString(text, LEFT + 2, 16);
        g.drawString("row " + (row + 1), LEFT + 2 + fm.stringWidth(text) + 14, 16);
    }

    /** Light-green band for the FST window selected in the result browser (drawn under the ticks). */
    private void drawHighlight(Graphics2D g, Slice slice, int height) {
        if (highlightChromosome == null || !highlightChromosome.equals(state.chromosome())) return;
        if (highlightEnd <= slice.from || highlightStart >= slice.end()) return;
        int x1 = Math.max(LEFT, slice.x(Math.max(highlightStart, slice.from)));
        int x2 = Math.min(slice.right(), slice.x(Math.min(highlightEnd, slice.end())));
        if (x2 <= x1) return;
        g.setColor(BrowserPalette.HIGHLIGHT_REGION);
        g.fillRect(x1, 0, x2 - x1, height);
    }

    /** Marks the genomic region of the currently selected FST window; 1-based inclusive. */
    public void setHighlightRegion(String chromosome, long start, long end) {
        highlightChromosome = chromosome;
        highlightStart = Math.max(0, start - 1);
        highlightEnd = Math.max(highlightStart + 1, end);
        repaint();
    }

    private void drawCodons(Graphics2D g, Slice slice, boolean reverse) {
        if (!showStrand(reverse)) return;
        int table = state.translationTable();
        String chromosome = state.chromosome();
        int length = state.length();
        for (int phase = 0; phase < 3; phase++) {
            int y = aaY(reverse, phase);
            if (state.pixelsPerBase() < 1.5) {
                g.setColor(BrowserPalette.AA_FORWARD);
                g.fillRect(LEFT, cellY(y), Math.max(0, slice.right() - LEFT), cellHeight());
                continue;
            }
            long first = Math.floorDiv((long) Math.floor(slice.from) - phase - 2, 3) * 3 + phase;
            for (long p = first; p < slice.end(); p += 3) {
                if (p < 0 || p + 3 > length) continue;
                String codon = codonAt(chromosome, p, reverse);
                char amino = CodonTable.aminoAcid(table, codon);
                boolean start = amino == 'M' && CodonTable.isStart(table, codon);
                boolean special = amino == '*' || start;
                Color fill = amino == '*' ? BrowserPalette.STOP_CODON : start ? BrowserPalette.START_CODON
                    : reverse ? BrowserPalette.AA_REVERSE : BrowserPalette.AA_FORWARD;
                int natural = Math.max(1, slice.x(p + 3) - slice.x(p) - 1);
                int width = special ? Math.max(3, natural) : natural;
                int x = slice.x(p);
                if (width > natural) x -= (width - natural) / 2;
                g.setColor(fill);
                g.fillRect(x, cellY(y), width, cellHeight());
                if (state.pixelsPerBase() >= 5) {
                    g.setColor(amino == '*' ? new Color(174, 45, 48) : new Color(60, 72, 78));
                    centered(g, String.valueOf(amino), slice.x(p + 1.5), y);
                }
            }
        }
    }

    /** 按链的方向读出一个密码子。 */
    private String codonAt(String chromosome, long p, boolean reverse) {
        if (!reverse) {
            return "" + data.baseAt(chromosome, p) + data.baseAt(chromosome, p + 1) + data.baseAt(chromosome, p + 2);
        }
        return "" + complement(data.baseAt(chromosome, p + 2)) + complement(data.baseAt(chromosome, p + 1))
            + complement(data.baseAt(chromosome, p));
    }

    /**
     * 选中基因的外显子框：颜色 (30,34,36)、2px、上下边缘正好卡住整行。
     * 每条显示中的碱基行都画（与基因自身的链无关），和拖拽选区同一套观感。
     */
    private void drawSelectedExonFrames(Graphics2D g, Slice slice) {
        if (selectedExons.isEmpty()) return;
        g.setColor(BrowserPalette.SELECTION);
        g.setStroke(new BasicStroke(2f));
        for (AnnotationIndex.Part part : selectedExons) {
            if (part.end <= slice.from || part.start >= slice.end()) continue;
            int x1 = slice.x(Math.max(part.start, slice.from));
            int x2 = slice.x(Math.min(part.end, slice.end()));
            int width = Math.max(1, x2 - x1);
            for (boolean reverse : new boolean[]{false, true}) {
                if (!showStrand(reverse)) continue;
                g.drawRect(x1, cellY(baseY(reverse)) - 1, width, cellHeight() + 1);
            }
        }
        g.setStroke(new BasicStroke(1f));
    }

    /**
     * 氨基酸行上的深灰底：每个 CDS 片段铺满该片段对应的相位行。
     * 只画在转录本自身那条链的行上。
     */
    private void drawSelectedCdsBand(Graphics2D g, Slice slice, boolean reverse) {
        if (selectedLocus == null) return;
        g.setColor(BrowserPalette.AA_FORWARD_SELECTED);
        for (AnnotationIndex.Transcript transcript : selectedLocus.transcripts) {
            if (transcript.cds.isEmpty() || (transcript.strand == '-') != reverse) continue;
            for (CdsSegment segment : cdsSegments(transcript)) {
                if (segment.end <= slice.from || segment.start >= slice.end()) continue;
                int x1 = slice.x(Math.max(segment.start, slice.from));
                int x2 = slice.x(Math.min(segment.end, slice.end()));
                g.fillRect(x1, cellY(aaY(reverse, segment.phase)), Math.max(1, x2 - x1), cellHeight());
            }
        }
    }

    /**
     * 灰底之上再叠每个密码子的小格与字母（左+1、上+2、宽-2、高 cellHeight-4），
     * 于是灰底只在四周留一条边。低倍时只画起始/终止密码子。
     */
    private void drawSelectedCodonCells(Graphics2D g, Slice slice, boolean reverse) {
        if (selectedLocus == null) return;
        int table = state.translationTable();
        String chromosome = state.chromosome();
        boolean coarse = state.pixelsPerBase() < 1.5;
        for (AnnotationIndex.Transcript transcript : selectedLocus.transcripts) {
            if (transcript.cds.isEmpty() || (transcript.strand == '-') != reverse) continue;
            for (CdsSegment segment : cdsSegments(transcript)) {
                if (segment.end <= slice.from || segment.start >= slice.end()) continue;
                int y = aaY(reverse, segment.phase);
                // 片段里所有完整密码子：最左碱基 p ≡ phase (mod 3)
                long first = segment.start + Math.floorMod(segment.phase - segment.start, 3);
                for (long p = first; p + 3 <= segment.end; p += 3) {
                    if (p + 3 <= slice.from || p >= slice.end()) continue;
                    String codon = codonAt(chromosome, p, reverse);
                    char amino = CodonTable.aminoAcid(table, codon);
                    boolean strongStart = amino == 'M' && CodonTable.isStart(table, codon);
                    boolean special = amino == '*' || strongStart;
                    if (coarse && !special) continue;
                    int x1 = slice.x(p);
                    int x2 = slice.x(p + 3);
                    int width = Math.max(1, x2 - x1 - 2);
                    if (special) width = Math.max(3, width);
                    g.setColor(amino == '*' ? BrowserPalette.STOP_CODON
                        : strongStart ? BrowserPalette.START_CODON
                        : reverse ? BrowserPalette.AA_REVERSE_SELECTED_CELL : BrowserPalette.AA_FORWARD_SELECTED_CELL);
                    g.fillRect(x1 + 1, cellY(y) + 2, width, Math.max(2, cellHeight() - 4));
                    if (state.pixelsPerBase() >= 5) {
                        g.setColor(amino == '*' ? new Color(174, 45, 48) : BrowserPalette.AA_SELECTED_TEXT);
                        centered(g, String.valueOf(amino), (x1 + x2) / 2, y);
                    }
                }
            }
        }
    }

    private void drawSelectionBoxes(Graphics2D g, Slice slice, boolean reverse) {
        if (selections.isEmpty() && selectionDrag == null) return;
        List<long[]> all = new ArrayList<>(selections);
        if (selectionDrag != null) all.add(selectionDrag);
        g.setColor(BrowserPalette.SELECTION);
        g.setStroke(new BasicStroke(1.6f));
        for (long[] range : all) {
            if (range[1] <= slice.from || range[0] >= slice.end()) continue;
            int x1 = slice.x(Math.max(range[0], slice.from));
            int x2 = slice.x(Math.min(range[1], slice.end()));
            int y = cellY(baseY(reverse));
            g.drawRect(x1, y - 1, Math.max(1, x2 - x1), cellHeight() + 1);
        }
        g.setStroke(new BasicStroke(1f));
    }

    /** 可见基因数超过该值时只画合并的基因条，跳过外显子/CDS/标签。 */
    private static final int SIMPLE_MODE_GENES = 6000;
    /** 可见基因数超过该值时不再绘制基因名字标签。 */
    private static final int LABEL_MODE_GENES = 1500;
    /**
     * CDS outlines are useful only at a regional scale. Keeping them out of
     * chromosome-scale paints avoids walking and drawing every CDS segment on
     * every pan or zoom event.
     */
    private static final double CDS_DETAIL_MAX_VISIBLE_BASES = 250_000.0;

    private void drawGenes(Graphics2D g, Slice slice, int width) {
        // 线性视图两侧各多取 2000bp，避免正好压在边界上的基因被裁掉；
        // 折行视图每行各管一段，取宽了会把相邻行的基因也算进来。
        int pad = wrapped ? 0 : 2000;
        int from = (int) Math.max(0, Math.floor(slice.from) - pad);
        int to = (int) Math.min(state.length(), Math.ceil(slice.end()) + pad);
        List<AnnotationIndex.Locus> loci = data.annotation().loci(state.chromosome(), from, to);
        int lanes = assignLanes(loci);
        // 折行时不能覆盖线性视图的命中缓存（locusAt 与选中框都依赖它）
        if (!wrapped) {
            visibleLoci = loci;
            laneCount = lanes;
        }
        geneHandles.clear();
        boolean simple = loci.size() > SIMPLE_MODE_GENES;
        boolean showLabels = loci.size() <= LABEL_MODE_GENES;
        boolean showCdsDetails = slice.count <= CDS_DETAIL_MAX_VISIBLE_BASES;
        List<int[]> labels = new ArrayList<>();
        List<String> labelText = new ArrayList<>();
        for (AnnotationIndex.Locus locus : loci) {
            int y = geneLaneY(locus);
            if (y < 0) continue;
            if (locus.start >= slice.end() || locus.end <= slice.from) continue;
            int barX1 = slice.x(Math.max(locus.start, slice.from));
            int barX2 = slice.x(Math.min(locus.end, slice.end()));
            if (barX1 < LEFT) barX1 = LEFT;
            if (simple) {
                // 全染色体级别：一条合并色块即可，避免逐个外显子/CDS/标签的开销
                g.setColor(locus.strand == '-' ? BrowserPalette.REVERSE : BrowserPalette.FORWARD);
                g.fillRect(barX1, y, Math.max(1, barX2 - barX1), 8);
                continue;
            }
            // gene backbone first, then exons/Cds on top of it
            g.setColor(BrowserPalette.GENE_BAR);
            g.fillRect(barX1, y + EXON_HEIGHT / 2 - 1, Math.max(1, barX2 - barX1), 3);
            for (AnnotationIndex.Transcript transcript : locus.transcripts) {
                drawTranscript(g, slice, locus, transcript, y, false);
            }
            // Add CDS boxes only after the view reaches a regional scale.
            if (showCdsDetails) {
                for (AnnotationIndex.Transcript transcript : locus.transcripts) {
                    drawCdsBoxes(g, slice, transcript, y);
                }
            }
            // Zoom-gated exon handles (click to jump to the adjacent exon; linear view only)
            if (!wrapped) {
                for (AnnotationIndex.Transcript transcript : locus.transcripts) {
                    drawHandles(g, slice, transcript, y);
                }
            }
            if (locus == selectedLocus) {
                g.setColor(new Color(210, 45, 48));
                g.setStroke(new BasicStroke(2f));
                g.drawRect(barX1 - 2, y - CDS_OVERHANG - 1, Math.max(1, barX2 - barX1 + 4), EXON_HEIGHT + CDS_OVERHANG * 2 + 2);
                g.setStroke(new BasicStroke(1f));
            }
            if (showLabels && barX1 >= LEFT) {
                labels.add(new int[]{barX1 + 3, y - LABEL_GAP});
                labelText.add(locus.displayName());
            }
        }
        drawLabels(g, labels, labelText, slice);
        requestGeneRelayoutIfNeeded();
    }

    /**
     * lane 数是绘制时才确定的，而 Swing 的尺寸不能在 paintComponent 里改。
     * 所以这里只做比较，真正的高度调整交给下一个事件循环。
     */
    private void requestGeneRelayoutIfNeeded() {
        if (wrapped) return;
        int needed = geneContentHeight();
        if (needed == appliedGeneHeight) return;
        appliedGeneHeight = needed;
        SwingUtilities.invokeLater(this::updateVisibility);
    }

    private void drawTranscript(Graphics2D g, Slice slice, AnnotationIndex.Locus locus,
                                AnnotationIndex.Transcript transcript, int y, boolean unused) {
        List<AnnotationIndex.Part> parts = transcript.exons;
        Color fill = transcript.strand == '-' ? BrowserPalette.REVERSE : BrowserPalette.FORWARD;
        for (AnnotationIndex.Part part : parts) {
            if (part.end <= slice.from || part.start >= slice.end()) continue;
            int x1 = slice.x(Math.max(part.start, slice.from));
            int x2 = slice.x(Math.min(part.end, slice.end()));
            if (x2 - x1 < 1) x2 = x1 + 1;
            g.setColor(fill);
            g.fillRect(x1, y, x2 - x1, EXON_HEIGHT);
            g.setColor(fill.darker());
            g.drawRect(x1, y, x2 - x1, EXON_HEIGHT - 1);
        }
    }

    /** Handles are only useful for introns longer than 60 bp and once the view is zoomed in. */
    private static final int HANDLE_MIN_INTRON_BP = 60;
    /** Minimum pixels per base before any handle is drawn, which bounds the handle count. */
    private static final double HANDLE_MIN_PPB = 0.2;
    private static final int HANDLE_RADIUS = 3;

    private final class GeneHandle {
        final Rectangle area;
        /** 点它之后要落到哪：同一条内含子对岸那个把手的位置。 */
        final long destination;
        GeneHandle(Rectangle area, long destination) {
            this.area = area;
            this.destination = destination;
        }
    }

    /**
     * 按内含子成对地画把手：一个内含子两端各一个，彼此互为归宿。
     * <pre>
     *   外显子A                内含子 ( > 60bp )                 外显子B
     *          |—— ○ 指针朝右                             指针朝左 ○ ——|
     *     锚在 A.end                                    锚在 B.start
     *   点左端把手 → 落到 B.start（右端把手的位置）
     *   点右端把手 → 落到 A.end  （左端把手的位置）
     * </pre>
     * 也就是"点一下跨过这条内含子到对岸"，来回点就是来回跨越它。
     * 锚点与指针方向都只由几何决定，与链无关（外显子一律按坐标升序存放）。
     */
    private void drawHandles(Graphics2D g, Slice slice, AnnotationIndex.Transcript transcript, int y) {
        if (state.pixelsPerBase() < HANDLE_MIN_PPB) return;
        List<AnnotationIndex.Part> parts = transcript.exons;
        for (int i = 0; i + 1 < parts.size(); i++) {
            AnnotationIndex.Part left = parts.get(i);
            AnnotationIndex.Part right = parts.get(i + 1);
            if (right.start - left.end <= HANDLE_MIN_INTRON_BP) continue;
            drawHandle(g, slice, left.end, y, 1, right.start);
            drawHandle(g, slice, right.start, y, -1, left.end);
        }
    }

    /**
     * PlastYes 式挂钩：外显子边界上的小圆点 + 朝内含子方向的短指针。
     *
     * @param anchor      画在哪：本侧外显子靠内含子的那条边界
     * @param direction   指针朝内含子的方向（左 -1 / 右 +1）
     * @param destination 点它之后去哪：对岸那个把手的位置
     */
    private void drawHandle(Graphics2D g, Slice slice, long anchor, int y, int direction, long destination) {
        int x = slice.x(anchor);
        if (x < LEFT || x > slice.right()) return;
        int cy = y + EXON_HEIGHT / 2;
        g.setColor(BrowserPalette.HANDLE);
        g.drawLine(x, cy, x + direction * 7, cy - 5);
        g.drawLine(x + direction * 7, cy - 5, x + direction * 12, cy - 5);
        g.setColor(Color.WHITE);
        g.fillOval(x - HANDLE_RADIUS, cy - HANDLE_RADIUS, HANDLE_RADIUS * 2, HANDLE_RADIUS * 2);
        g.setColor(BrowserPalette.HANDLE);
        g.drawOval(x - HANDLE_RADIUS, cy - HANDLE_RADIUS, HANDLE_RADIUS * 2, HANDLE_RADIUS * 2);
        geneHandles.add(new GeneHandle(new Rectangle(x - 5, cy - 5, 11, 11), destination));
    }

    private boolean activateHandleAt(Point point) {
        for (int i = geneHandles.size() - 1; i >= 0; i--) {
            GeneHandle handle = geneHandles.get(i);
            if (!handle.area.contains(point)) continue;
            // 跨过这条内含子，落到对岸那个把手的位置
            state.jump(handle.destination, viewWidth());
            updateVisibility();
            repaint();
            return true;
        }
        return false;
    }

    private void drawCdsBoxes(Graphics2D g, Slice slice, AnnotationIndex.Transcript transcript, int y) {
        if (transcript.cds.isEmpty()) return;
        g.setColor(BrowserPalette.CDS_BOX);
        g.setStroke(new BasicStroke(1f));
        for (AnnotationIndex.Part part : transcript.cds) {
            if (part.end <= slice.from || part.start >= slice.end()) continue;
            int x1 = slice.x(Math.max(part.start, slice.from));
            int x2 = slice.x(Math.min(part.end, slice.end()));
            g.drawRect(x1 - 1, y - CDS_OVERHANG, Math.max(2, x2 - x1 + 2), EXON_HEIGHT + CDS_OVERHANG * 2);
        }
        // connect CDS boxes with a thin line when zoomed out
        for (int i = 1; i < transcript.cds.size(); i++) {
            AnnotationIndex.Part previous = transcript.cds.get(i - 1);
            AnnotationIndex.Part part = transcript.cds.get(i);
            if (previous.end < slice.from || part.start > slice.end()) continue;
            g.drawLine(slice.x(previous.end), y + EXON_HEIGHT / 2, slice.x(part.start), y + EXON_HEIGHT / 2);
        }
    }

    private void drawLabels(Graphics2D g, List<int[]> positions, List<String> texts, Slice slice) {
        g.setFont(uiFont());
        FontMetrics fm = g.getFontMetrics();
        List<Rectangle> occupied = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            int x = positions.get(i)[0];
            int baseline = positions.get(i)[1];
            String text = texts.get(i);
            Rectangle area = new Rectangle(x - 2, baseline - fm.getAscent(), fm.stringWidth(text) + 4, fm.getHeight());
            if (area.x < LEFT || area.x > slice.right()) continue;
            boolean overlaps = false;
            for (Rectangle previous : occupied) if (previous.intersects(area)) { overlaps = true; break; }
            if (overlaps) continue;
            occupied.add(area);
            g.setColor(BrowserPalette.LABEL_TEXT);
            g.drawString(text, x, baseline);
        }
    }

    private int assignLanes(List<AnnotationIndex.Locus> loci) {
        List<Integer> ends = new ArrayList<>();
        for (AnnotationIndex.Locus locus : loci) {
            int lane = -1;
            for (int i = 0; i < ends.size(); i++) {
                if (ends.get(i) <= locus.start) { lane = i; ends.set(i, locus.end); break; }
            }
            if (lane < 0) { lane = ends.size(); ends.add(locus.end); }
            locus.lane = lane;
        }
        return Math.max(1, ends.size());
    }

    // ---------------------------------------------------------------- interaction helpers

    /** 折行视图每行的像素宽度（不含左右留白）；以轨道自身宽度为准，保证与 Slice 的换算一致。 */
    private int wrappedInnerWidth() {
        int width = wrappedTrack.getWidth();
        if (width <= 0) width = wrappedScroll.getViewport().getExtentSize().width;
        if (width <= 0) width = contentWidth();
        return Math.max(1, width - LEFT - RIGHT);
    }

    /** 折行视图每行放多少个碱基（由当前缩放决定）。 */
    private int wrappedBasesPerRow() {
        return Math.max(1, (int) Math.floor(wrappedInnerWidth() * state.basesPerPixel() + 1e-8));
    }

    /** 折行视图每一行的高度：标尺 + 序列区 + 基因区 + 行间隙。 */
    private int wrappedRowHeight() {
        return sequenceHeight() + wrappedGeneAreaHeight() + ROW_GAP;
    }

    private int wrappedRowCount() {
        if (state.length() <= 0) return 1;
        return Math.max(1, (int) Math.ceil(state.length() / (double) wrappedBasesPerRow()));
    }

    /**
     * 折行视图的内容高度必须自己算好再告诉 Swing：
     * JViewport 只会把面板拉成视口大小，多出来的行否则永远滚不到。
     * 尺寸真的变了才 setPreferredSize，避免反复 revalidate。
     */
    private void updateWrappedLayout() {
        int rowHeight = wrappedRowHeight();
        int rows = wrappedRowCount();
        int width = wrappedTrack.getWidth() > 0
            ? wrappedTrack.getWidth()
            : Math.max(200, wrappedScroll.getViewport().getExtentSize().width);
        // 滚轮一格走半行，滚动才跟得上“一行一行”的内容
        JScrollBar bar = wrappedScroll.getVerticalScrollBar();
        int unit = Math.max(16, rowHeight / 2);
        if (bar.getUnitIncrement() != unit) bar.setUnitIncrement(unit);
        Dimension size = new Dimension(width, Math.max(rowHeight, rows * rowHeight));
        if (!size.equals(wrappedTrack.getPreferredSize())) {
            wrappedTrack.setPreferredSize(size);
            wrappedTrack.revalidate();
        }
    }

    private int wrappedRowFor(double coordinate) {
        return (int) Math.max(0, Math.floor(coordinate / (double) Math.max(1, wrappedBasesPerRow())));
    }

    private int wrappedFirstVisibleRow() {
        return Math.max(0, wrappedScroll.getViewport().getViewPosition().y / Math.max(1, wrappedRowHeight()));
    }

    /** 请求滚动到第 row 行；视口还没定型时会留给 resize 回调重试。 */
    private void scrollToWrappedRow(int row) {
        pendingWrappedRow = Math.max(0, row);
        applyPendingWrappedRow();
    }

    /**
     * 真正执行滚动。
     * 内容高度变了之后，滚动条模型要经过一次布局才会跟上；而刚切进折行视图时
     * 视口可能还是 0 尺寸。此时 setValue 会被夹回 0（表现为“没有跳到目标行”），
     * 所以这里以“视口位置是否真的到位”为准，没到位就留着 pending，
     * 由 viewport 的 change/resize 回调再补一次。
     */
    /**
     * 基因区高度是固定的（{@link #WRAPPED_VISIBLE_LANES} 条 lane），
     * 数据需要更多 lane 时就显示这条滚动条来平移 lane 窗口。
     * 显隐会改变折行区宽度，所以调用方要接着重算布局。
     */
    private void updateWrappedLaneBar() {
        int lanes = wrappedLaneCount();
        boolean needed = lanes > WRAPPED_VISIBLE_LANES;
        wrappedLaneOffset = needed ? Math.max(0, Math.min(wrappedLaneOffset, lanes - WRAPPED_VISIBLE_LANES)) : 0;
        if (wrappedLaneBar.isVisible() != needed) {
            wrappedLaneBar.setVisible(needed);
            wrappedCard.revalidate();
        }
        if (needed) {
            wrappedLaneBar.setValues(wrappedLaneOffset, WRAPPED_VISIBLE_LANES, 0, lanes);
        }
    }

    private void applyPendingWrappedRow() {
        if (applyingWrappedRow) return;
        if (!wrapped || pendingWrappedRow < 0) return;
        JViewport viewport = wrappedScroll.getViewport();
        int extent = viewport.getExtentSize().height;
        if (extent <= 0) return;
        applyingWrappedRow = true;
        try {
            updateWrappedLayout();
            wrappedScroll.validate();
            int rowHeight = wrappedRowHeight();
            int content = wrappedRowCount() * (double) rowHeight > Integer.MAX_VALUE
                ? Integer.MAX_VALUE : wrappedRowCount() * rowHeight;
            int value = Math.max(0, Math.min(pendingWrappedRow * rowHeight, Math.max(0, content - extent)));
            // 只动 viewport：滚动条是它的从属，反过来再 setValue 会触发 AdjustmentEvent
            // 把 viewport 又拽回旧位置（旧的 maximum 还没跟上内容高度时尤其明显）。
            viewport.setViewPosition(new Point(viewport.getViewPosition().x, value));
            if (viewport.getViewPosition().y == value) pendingWrappedRow = -1;
        } finally {
            applyingWrappedRow = false;
        }
    }

    private double rowAt(int y) { return y / (double) Math.max(1, wrappedRowHeight()); }

    private long coordinateAt(int x) {
        if (state.length() == 0) return -1;
        double coordinate = state.start() + Math.max(0, x - LEFT) * state.basesPerPixel();
        return trim(coordinate);
    }

    private long trim(double coordinate) {
        if (state.length() == 0) return 0;
        return Math.max(0, Math.min(state.length() - 1L, Math.round(coordinate)));
    }

    private boolean insideSelection(long coordinate) {
        for (long[] range : selections) if (coordinate >= range[0] && coordinate < range[1]) return true;
        return false;
    }

    /**
     * 与 {@link #assignLanes} 同样的 lane 排法，但结果只放数组、不写回 locus。
     * 折行视图每行各自排 lane，写回共享对象会污染线性视图的命中缓存。
     */
    private int[] lanesOf(List<AnnotationIndex.Locus> loci) {
        List<Integer> ends = new ArrayList<>();
        int[] lanes = new int[loci.size()];
        for (int k = 0; k < loci.size(); k++) {
            AnnotationIndex.Locus locus = loci.get(k);
            int lane = -1;
            for (int i = 0; i < ends.size(); i++) {
                if (ends.get(i) <= locus.start) {
                    lane = i;
                    ends.set(i, locus.end);
                    break;
                }
            }
            if (lane < 0) {
                lane = ends.size();
                ends.add(locus.end);
            }
            lanes[k] = lane;
        }
        return lanes;
    }

    /**
     * 折行视图的命中测试：先按行高定位行，再按 lane 窗口（要加上基因区滚动条的偏移）
     * 定位 lane，最后用该行的基因组坐标判断落在哪个基因里。
     */
    private AnnotationIndex.Locus locusAtWrapped(Point point) {
        if (state.length() == 0 || point.x < LEFT) return null;
        int rowHeight = wrappedRowHeight();
        int row = Math.floorDiv(point.y, Math.max(1, rowHeight));
        int geneTop = row * rowHeight + sequenceHeight();
        if (point.y < geneTop) return null;
        int window = Math.floorDiv(point.y - geneTop - geneTopPad(), Math.max(1, laneHeight()));
        if (window < 0 || window >= WRAPPED_VISIBLE_LANES) return null;
        int lane = window + wrappedLaneOffset;
        long bases = wrappedBasesPerRow();
        long from = row * bases;
        if (from >= state.length()) return null;
        double coordinate = from + Math.max(0, point.x - LEFT) * state.basesPerPixel();
        List<AnnotationIndex.Locus> loci = data.annotation().loci(state.chromosome(),
            (int) Math.max(0, from), (int) Math.min(state.length(), from + Math.max(1, bases)));
        int[] lanes = lanesOf(loci);
        for (int i = 0; i < loci.size(); i++) {
            AnnotationIndex.Locus locus = loci.get(i);
            if (lanes[i] != lane) continue;
            if (coordinate >= locus.start && coordinate < locus.end) return locus;
        }
        return null;
    }

    private AnnotationIndex.Locus locusAt(Point point) {
        int laneHeight = laneHeight();
        for (AnnotationIndex.Locus locus : visibleLoci) {
            int y = laneTop(locus.lane);
            if (point.y < y - CDS_OVERHANG - 2 || point.y > y + EXON_HEIGHT + CDS_OVERHANG + 2) continue;
            int x1 = sliceX(locus.start);
            int x2 = sliceX(locus.end);
            if (point.x >= Math.min(x1, x2) - 3 && point.x <= Math.max(x1, x2) + 3) return locus;
        }
        return null;
    }

    private int sliceX(double position) {
        return LEFT + (int) Math.round((position - state.start()) * state.pixelsPerBase());
    }

    private void reportPosition(Point point) {
        long position = -1;
        if (point != null && state.length() > 0) {
            double coordinate = coordinateAtPoint(point);
            if (coordinate >= 0 && coordinate < state.length()) position = Math.round(coordinate) + 1;
        }
        if (coordinateListener != null) coordinateListener.accept(position);
    }

    private void showSelectionMenu(MouseEvent e) {
        if (!insideSelection(coordinateAt(e.getX()))) return;
        JPopupMenu menu = new JPopupMenu();
        menu.add(copyItem("Copy forward DNA", false, false));
        menu.add(copyItem("Copy reverse DNA", true, false));
        menu.addSeparator();
        menu.add(copyItem("Copy forward amino acids", false, true));
        menu.add(copyItem("Copy reverse amino acids", true, true));
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private JMenuItem copyItem(String label, boolean reverse, boolean amino) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(selectedValue(reverse, amino)), null));
        return item;
    }

    // ---------------------------------------------------------------- small helpers

    private void centered(Graphics2D g, String text, int center, int rowTop) {
        Font font = new Font(Font.MONOSPACED, Font.BOLD, Math.max(14, uiFont().getSize()));
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, center - fm.stringWidth(text) / 2,
            cellY(rowTop) + (cellHeight() - fm.getHeight()) / 2 + fm.getAscent());
    }

    private Graphics2D quality(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setFont(uiFont());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    private static char complement(char base) {
        switch (Character.toUpperCase(base)) {
            case 'A': return 'T';
            case 'T': case 'U': return 'A';
            case 'C': return 'G';
            case 'G': return 'C';
            case 'R': return 'Y';
            case 'Y': return 'R';
            case 'K': return 'M';
            case 'M': return 'K';
            case 'B': return 'V';
            case 'V': return 'B';
            case 'D': return 'H';
            case 'H': return 'D';
            case 'S': case 'W': return base;
            default: return 'N';
        }
    }

    private static String reverseComplement(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = value.length() - 1; i >= 0; i--) builder.append(complement(value.charAt(i)));
        return builder.toString();
    }

    private static long niceStep(double target) {
        long p = 1;
        while (p * 10 < target) p *= 10;
        if (target <= p) return p;
        if (target <= p * 2) return p * 2;
        if (target <= p * 5) return p * 5;
        return p * 10;
    }

    private static String formatPosition(long position) {
        return String.format(java.util.Locale.US, "%,d", position);
    }
}
