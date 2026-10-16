import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.TextAttribute;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** DayFlow Planner v1.0 - plain Java (Swing, no external libraries). */
public class Main {

    // ======================= Model =======================
    enum Priority { LOW, MEDIUM, HIGH }

    static class Task implements Serializable {
        private static final long serialVersionUID = 1L;
        String title;
        LocalDate date;
        LocalTime time;          // optional
        Priority priority;
        boolean isCompleted;

        Task(String title, LocalDate date, LocalTime time, Priority priority) {
            this.title = title; this.date = date; this.time = time; this.priority = priority;
        }
    }

    // ======================= Theme =======================
    static final Color BG = new Color(0x0f1115), PANEL = new Color(0x171a21), BORDER = new Color(0x262b36);
    static final Color TEXT = new Color(0xe5e7eb), MUTED = new Color(0x8b93a1), DIM = new Color(0x6b7280);
    static final Color ACCENT = new Color(0x38bdf8), GREEN = new Color(0x22c55e), RED = new Color(0xef4444);

    static Font font(int style, int size) { return new Font("Segoe UI", style, size); }

    static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g2;
    }

    static JLabel label(String text, Font f, Color c) {
        JLabel l = new JLabel(text);
        l.setFont(f);
        l.setForeground(c);
        l.putClientProperty("html.disable", Boolean.TRUE); // never render user text as HTML
        return l;
    }

    // ======================= Custom components =======================
    static class RoundPanel extends JPanel {
        final Color fill, line; final int arc;
        RoundPanel(LayoutManager lm, Color fill, Color line, int arc) {
            super(lm); this.fill = fill; this.line = line; this.arc = arc; setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            if (line != null) { g2.setColor(line); g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc); }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class RoundButton extends JButton {
        final Color fill, hover, line;
        RoundButton(String text, Color fill, Color hover, Color fg, Color line) {
            super(text);
            this.fill = fill; this.hover = hover; this.line = line;
            setForeground(fg);
            setFont(font(Font.BOLD, 13));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(9, 16, 9, 16));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            Color c = getModel().isRollover() ? hover : fill;
            if (c != null) { g2.setColor(c); g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16); }
            if (line != null) { g2.setColor(line); g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16); }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static RoundButton primary(String t) { return new RoundButton(t, ACCENT, new Color(0x7dd3fc), new Color(0x0b0d12), null); }
    static RoundButton ghost(String t) { return new RoundButton(t, PANEL, BORDER, TEXT, BORDER); }

    static class HintField extends JTextField {
        final String hint;
        boolean invalid;
        HintField(String hint, int cols) {
            super(cols);
            this.hint = hint;
            setOpaque(false);
            setForeground(TEXT);
            setCaretColor(TEXT);
            setSelectionColor(new Color(0x1d4e6b));
            setFont(font(Font.PLAIN, 14));
            setBorder(new EmptyBorder(9, 12, 9, 12));
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setColor(PANEL);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g2.setColor(invalid ? RED : isFocusOwner() ? ACCENT : BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g2.dispose();
            super.paintComponent(g);
            if (getText().isEmpty()) {
                Graphics2D h = aa(g);
                h.setColor(DIM);
                h.setFont(getFont());
                FontMetrics fm = h.getFontMetrics();
                h.drawString(hint, getInsets().left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                h.dispose();
            }
        }
    }

    static class ProgressView extends JComponent {
        double value, target;
        final Timer anim;
        ProgressView() {
            setPreferredSize(new Dimension(100, 12));
            anim = new Timer(15, e -> {
                value += (target - value) * 0.18;
                if (Math.abs(target - value) < 0.002) { value = target; ((Timer) e.getSource()).stop(); }
                repaint();
            });
        }
        void set(double t, boolean animate) {
            target = t;
            if (animate) anim.start(); else { anim.stop(); value = t; repaint(); }
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int w = getWidth(), h = getHeight();
            g2.setColor(BORDER);
            g2.fillRoundRect(0, 0, w, h, h, h);
            int bw = (int) Math.round(w * value);
            if (bw > 0) {
                g2.setPaint(new GradientPaint(0, 0, ACCENT, w, 0, GREEN));
                g2.fillRoundRect(0, 0, Math.max(bw, h), h, h, h);
            }
            g2.dispose();
        }
    }

    static class CheckIcon implements Icon {
        public int getIconWidth() { return 20; }
        public int getIconHeight() { return 20; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            boolean on = ((AbstractButton) c).isSelected();
            Graphics2D g2 = aa(g);
            g2.translate(x, y);
            if (on) {
                g2.setColor(GREEN);
                g2.fillRoundRect(0, 0, 19, 19, 7, 7);
                g2.setColor(new Color(0x0b0d12));
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawPolyline(new int[]{5, 9, 15}, new int[]{10, 14, 6}, 3);
            } else {
                g2.setColor(BG);
                g2.fillRoundRect(0, 0, 19, 19, 7, 7);
                g2.setColor(new Color(0x4b5563));
                g2.drawRoundRect(0, 0, 19, 19, 7, 7);
            }
            g2.dispose();
        }
    }

    static class Pill extends JLabel {
        final Color bg;
        Pill(String text, Color bg, Color fg) {
            super(text);
            this.bg = bg;
            setFont(font(Font.BOLD, 10));
            setForeground(fg);
            setOpaque(false);
            setBorder(new EmptyBorder(3, 10, 3, 10));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class ListPanel extends JPanel implements Scrollable {
        ListPanel() { setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setOpaque(false); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(24, r.height - 24); }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    static class DarkScrollUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() { thumbColor = new Color(0x3a4150); trackColor = BG; }
        private JButton none() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
        @Override protected JButton createDecreaseButton(int o) { return none(); }
        @Override protected JButton createIncreaseButton(int o) { return none(); }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2 = aa(g);
            g2.setColor(thumbColor);
            g2.fillRoundRect(r.x + 2, r.y, r.width - 4, r.height, 8, 8);
            g2.dispose();
        }
    }

    static <T> void styleCombo(JComboBox<T> c) {
        c.setUI(new BasicComboBoxUI() {
            @Override protected JButton createArrowButton() {
                BasicArrowButton b = new BasicArrowButton(BasicArrowButton.SOUTH, PANEL, PANEL, MUTED, PANEL);
                b.setBorder(new EmptyBorder(0, 0, 0, 0));
                return b;
            }
        });
        c.setBackground(PANEL);
        c.setForeground(TEXT);
        c.setFont(font(Font.PLAIN, 14));
        c.setBorder(new LineBorder(BORDER, 1, true));
        c.setPreferredSize(new Dimension(110, 38));
        c.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                JLabel r = (JLabel) super.getListCellRendererComponent(l, v, i, s, f);
                r.setBackground(s ? BORDER : PANEL);
                r.setForeground(TEXT);
                r.setBorder(new EmptyBorder(5, 10, 5, 10));
                return r;
            }
        });
    }

    // ======================= State =======================
    private static final File DATA_FILE = new File("tasks_data.dat");
    private static final File SETTINGS_FILE = new File("settings.properties");
    private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter CLOCK_NO_MS_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // performance settings
    private int clockFps = 60;                 // 60 / 30 / 10 / 1
    private boolean showMillis = true;
    private boolean animations = true;
    private boolean pauseInBackground = true;  // 1 FPS when the window is not focused
    private boolean windowFocused = true;

    private final List<Task> masterTaskList = new ArrayList<>();
    private LocalDate selectedDate = LocalDate.now();

    private JFrame frame;
    private JLabel clockLabel, dateLabel, statsLabel, percentLabel;
    private ProgressView progress;
    private HintField dateField, searchField, titleField, timeField;
    private JComboBox<Priority> priorityBox;
    private ListPanel listPanel;
    private Timer clockTimer;

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new Main().start());
    }

    // ======================= UI =======================
    private void start() {
        loadSettings();
        loadTasks();

        // ---- Header: precision clock + date navigation
        clockLabel = label("", new Font(Font.MONOSPACED, Font.BOLD, 44), ACCENT);
        dateLabel = label("", font(Font.PLAIN, 12), MUTED);
        JPanel clockBox = new JPanel();
        clockBox.setLayout(new BoxLayout(clockBox, BoxLayout.Y_AXIS));
        clockBox.setOpaque(false);
        clockBox.add(clockLabel);
        clockBox.add(dateLabel);

        dateField = new HintField("yyyy-mm-dd", 8);
        dateField.setText(selectedDate.toString());
        dateField.addActionListener(e -> {
            try { setDate(LocalDate.parse(dateField.getText().trim())); }
            catch (Exception ex) { dateField.invalid = true; dateField.repaint(); }
        });
        RoundButton prev = ghost("\u2039"), next = ghost("\u203A"), today = ghost("Today"), settings = ghost("Settings");
        prev.addActionListener(e -> setDate(selectedDate.minusDays(1)));
        next.addActionListener(e -> setDate(selectedDate.plusDays(1)));
        today.addActionListener(e -> setDate(LocalDate.now()));
        settings.addActionListener(e -> openSettings());
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        nav.setOpaque(false);
        nav.add(prev); nav.add(dateField); nav.add(next); nav.add(today); nav.add(settings);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(clockBox, BorderLayout.WEST);
        header.add(nav, BorderLayout.EAST);

        // ---- Daily progress
        statsLabel = label("", font(Font.PLAIN, 12), MUTED);
        percentLabel = label("0%", font(Font.BOLD, 18), ACCENT);
        progress = new ProgressView();
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(label("Daily Progress", font(Font.PLAIN, 14), TEXT), BorderLayout.WEST);
        top.add(statsLabel, BorderLayout.EAST);
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(false);
        bar.add(progress, BorderLayout.CENTER);
        bar.add(percentLabel, BorderLayout.EAST);
        RoundPanel progressPanel = new RoundPanel(new BorderLayout(0, 10), PANEL, null, 16);
        progressPanel.setBorder(new EmptyBorder(16, 16, 16, 16));
        progressPanel.add(top, BorderLayout.NORTH);
        progressPanel.add(bar, BorderLayout.CENTER);

        // ---- Add form
        titleField = new HintField("What do you need to get done?", 0);
        timeField = new HintField("HH:mm", 5);
        priorityBox = new JComboBox<>(Priority.values());
        priorityBox.setSelectedItem(Priority.MEDIUM);
        styleCombo(priorityBox);
        RoundButton addBtn = primary("+ Add New Task");
        addBtn.addActionListener(e -> addTask());
        titleField.addActionListener(e -> addTask());
        timeField.addActionListener(e -> addTask());
        JPanel addRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        addRight.setOpaque(false);
        addRight.add(timeField); addRight.add(priorityBox); addRight.add(addBtn);
        JPanel addRow = new JPanel(new BorderLayout(8, 0));
        addRow.setOpaque(false);
        addRow.add(titleField, BorderLayout.CENTER);
        addRow.add(addRight, BorderLayout.EAST);

        // ---- Search + clear done
        searchField = new HintField("Search tasks...", 0);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(false); }
            public void removeUpdate(DocumentEvent e) { refresh(false); }
            public void changedUpdate(DocumentEvent e) { }
        });
        RoundButton clearDone = ghost("Clear completed");
        clearDone.addActionListener(e -> {
            masterTaskList.removeIf(t -> t.date.equals(selectedDate) && t.isCompleted);
            refresh(true);
        });
        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(clearDone, BorderLayout.EAST);

        // ---- Task list
        listPanel = new ListPanel();
        JScrollPane scroll = new JScrollPane(listPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUI(new DarkScrollUI());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        scroll.getVerticalScrollBar().setUnitIncrement(24);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(false);
        north.add(header);
        north.add(Box.createVerticalStrut(16));
        north.add(progressPanel);
        north.add(Box.createVerticalStrut(16));
        north.add(addRow);
        north.add(Box.createVerticalStrut(12));
        north.add(searchRow);
        north.add(Box.createVerticalStrut(14));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(24, 24, 24, 24));
        root.add(north, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);

        frame = new JFrame("DayFlow Planner");
        frame.setContentPane(root);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(820, 860);
        frame.setMinimumSize(new Dimension(700, 560));
        frame.setLocationRelativeTo(null);
        frame.addWindowFocusListener(new WindowAdapter() {
            @Override public void windowGainedFocus(WindowEvent e) { windowFocused = true; applyClockRate(); }
            @Override public void windowLostFocus(WindowEvent e) { windowFocused = false; applyClockRate(); }
        });

        // ---- Precision clock (rate controlled by settings)
        clockTimer = new Timer(16, e -> tick());
        applyClockRate();
        tick();
        clockTimer.start();
        new Timer(30_000, e -> refresh(false)).start(); // keeps "Overdue" status current

        refresh(false);
        frame.setVisible(true);
    }

    // ======================= Clock =======================
    private boolean inBackground() { return pauseInBackground && !windowFocused; }

    private void tick() {
        String t = (showMillis && !inBackground() ? CLOCK_FMT : CLOCK_NO_MS_FMT).format(LocalTime.now());
        if (!t.equals(clockLabel.getText())) clockLabel.setText(t);
        String d = DATE_FMT.format(LocalDate.now());
        if (!d.equals(dateLabel.getText())) dateLabel.setText(d);
    }

    private void applyClockRate() {
        int fps = inBackground() ? 1 : (showMillis ? clockFps : Math.min(clockFps, 4));
        clockTimer.setDelay(Math.max(1, 1000 / fps));
    }

    // ======================= Logic =======================
    private void setDate(LocalDate d) {
        selectedDate = d;
        dateField.setText(d.toString());
        dateField.invalid = false;
        dateField.repaint();
        refresh(false);
    }

    private void addTask() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) { titleField.invalid = true; titleField.repaint(); titleField.requestFocusInWindow(); return; }
        titleField.invalid = false;
        LocalTime time = null;
        String tt = timeField.getText().trim();
        if (!tt.isEmpty()) {
            try { time = LocalTime.parse(tt.length() == 4 ? "0" + tt : tt); }
            catch (Exception ex) { timeField.invalid = true; timeField.repaint(); return; }
        }
        timeField.invalid = false;
        masterTaskList.add(new Task(title, selectedDate, time, (Priority) priorityBox.getSelectedItem()));
        titleField.setText("");
        timeField.setText("");
        refresh(true);
        titleField.requestFocusInWindow();
    }

    /** Rebuild the view and progress; persist only when data changed. */
    private void refresh(boolean persist) {
        rebuildList();
        updateProgress();
        if (persist) saveTasks();
        listPanel.revalidate();
        listPanel.repaint();
    }

    private void rebuildList() {
        String q = searchField.getText().trim().toLowerCase();
        List<Task> view = masterTaskList.stream()
                .filter(t -> t.date.equals(selectedDate) && (q.isEmpty() || t.title.toLowerCase().contains(q)))
                .sorted(Comparator.comparing((Task t) -> t.isCompleted)
                        .thenComparing(t -> t.time == null ? LocalTime.MAX : t.time)
                        .thenComparing(t -> -t.priority.ordinal()))
                .collect(Collectors.toList());
        listPanel.removeAll();
        if (view.isEmpty()) {
            JPanel empty = new JPanel(new FlowLayout(FlowLayout.CENTER));
            empty.setOpaque(false);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            empty.add(label("No tasks for this day. Add one above.", font(Font.PLAIN, 13), DIM));
            listPanel.add(empty);
        }
        for (Task t : view) {
            listPanel.add(createCard(t));
            listPanel.add(Box.createVerticalStrut(8));
        }
    }

    private void updateProgress() {
        long total = masterTaskList.stream().filter(t -> t.date.equals(selectedDate)).count();
        long done = masterTaskList.stream().filter(t -> t.date.equals(selectedDate) && t.isCompleted).count();
        double p = total == 0 ? 0 : (double) done / total;
        progress.set(p, animations);
        percentLabel.setText(Math.round(p * 100) + "%");
        statsLabel.setText(done + " / " + total + " completed");
    }

    private JComponent createCard(Task t) {
        RoundPanel card = new RoundPanel(new BorderLayout(12, 0), PANEL, BORDER, 16);
        card.setBorder(new EmptyBorder(12, 14, 12, 14));

        JCheckBox cb = new JCheckBox();
        CheckIcon icon = new CheckIcon();
        cb.setIcon(icon);
        cb.setSelectedIcon(icon);
        cb.setOpaque(false);
        cb.setFocusPainted(false);
        cb.setSelected(t.isCompleted);
        cb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cb.addActionListener(e -> { t.isCompleted = cb.isSelected(); SwingUtilities.invokeLater(() -> refresh(true)); });

        JLabel title = label(t.title, font(Font.BOLD, 15), t.isCompleted ? DIM : TEXT);
        if (t.isCompleted) {
            Map<TextAttribute, Object> attrs = new HashMap<>();
            attrs.put(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON);
            title.setFont(title.getFont().deriveFont(attrs));
        }
        boolean overdue = !t.isCompleted && t.time != null && (t.date.isBefore(LocalDate.now())
                || (t.date.equals(LocalDate.now()) && t.time.isBefore(LocalTime.now())));
        String metaText = (t.time != null ? "at " + TIME_FMT.format(t.time) : "No time set") + (overdue ? "  -  Overdue" : "");
        JLabel meta = label(metaText, font(Font.PLAIN, 12), overdue ? RED : MUTED);
        JPanel text = new JPanel(new GridLayout(2, 1, 0, 2));
        text.setOpaque(false);
        text.add(title);
        text.add(meta);

        Color[] badge = t.priority == Priority.HIGH ? new Color[]{new Color(0x3b1219), new Color(0xf87171)}
                : t.priority == Priority.MEDIUM ? new Color[]{new Color(0x3a2f12), new Color(0xfbbf24)}
                : new Color[]{new Color(0x12301f), new Color(0x4ade80)};
        RoundButton del = new RoundButton("\u00D7", null, new Color(0x3b1219), RED, null);
        del.setFont(font(Font.BOLD, 18));
        del.setBorder(new EmptyBorder(0, 10, 2, 10));
        del.addActionListener(e -> { masterTaskList.remove(t); SwingUtilities.invokeLater(() -> refresh(true)); });
        Box right = Box.createHorizontalBox();
        right.add(new Pill(t.priority.name(), badge[0], badge[1]));
        right.add(Box.createHorizontalStrut(10));
        right.add(del);

        card.add(cb, BorderLayout.WEST);
        card.add(text, BorderLayout.CENTER);
        card.add(right, BorderLayout.EAST);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        card.setPreferredSize(new Dimension(10, card.getPreferredSize().height)); // width follows the viewport
        return card;
    }

    // ======================= Settings =======================
    private JCheckBox check(String text, boolean on, Consumer<Boolean> action) {
        JCheckBox c = new JCheckBox(text, on);
        CheckIcon icon = new CheckIcon();
        c.setIcon(icon);
        c.setSelectedIcon(icon);
        c.setOpaque(false);
        c.setFocusPainted(false);
        c.setForeground(TEXT);
        c.setFont(font(Font.PLAIN, 14));
        c.setIconTextGap(10);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.addActionListener(e -> { action.accept(c.isSelected()); saveSettings(); });
        return c;
    }

    private void openSettings() {
        JDialog d = new JDialog(frame, "Performance Settings", true);
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(20, 24, 20, 24));

        JComboBox<Integer> fps = new JComboBox<>(new Integer[]{60, 30, 10, 1});
        styleCombo(fps);
        fps.setPreferredSize(new Dimension(80, 36));
        fps.setSelectedItem(clockFps);
        fps.addActionListener(e -> { clockFps = (Integer) fps.getSelectedItem(); applyClockRate(); saveSettings(); });
        JPanel row = new JPanel(new BorderLayout(16, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(label("Clock refresh rate (FPS)", font(Font.PLAIN, 14), TEXT), BorderLayout.WEST);
        row.add(fps, BorderLayout.EAST);

        Runtime rt = Runtime.getRuntime();
        JLabel hint = label("Lower clock FPS = less CPU / battery usage.", font(Font.PLAIN, 12), MUTED);
        JLabel mem = label("Memory in use: " + (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024) + " MB",
                font(Font.PLAIN, 12), MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        mem.setAlignmentX(Component.LEFT_ALIGNMENT);
        RoundButton close = ghost("Close");
        close.setAlignmentX(Component.LEFT_ALIGNMENT);
        close.addActionListener(e -> d.dispose());

        p.add(row);
        p.add(Box.createVerticalStrut(16));
        p.add(check("Show milliseconds", showMillis, v -> { showMillis = v; applyClockRate(); }));
        p.add(Box.createVerticalStrut(10));
        p.add(check("Smooth progress animation", animations, v -> animations = v));
        p.add(Box.createVerticalStrut(10));
        p.add(check("Save power when window is in background", pauseInBackground, v -> { pauseInBackground = v; applyClockRate(); }));
        p.add(Box.createVerticalStrut(16));
        p.add(hint);
        p.add(Box.createVerticalStrut(4));
        p.add(mem);
        p.add(Box.createVerticalStrut(16));
        p.add(close);

        d.setContentPane(p);
        d.pack();
        d.setLocationRelativeTo(frame);
        d.setVisible(true);
    }

    // ======================= Persistence =======================
    private void saveTasks() {
        Path tmp = Paths.get(DATA_FILE.getPath() + ".tmp");
        try {
            try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(tmp))) {
                out.writeObject(new ArrayList<>(masterTaskList));
            }
            Files.move(tmp, DATA_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING); // safe write
        } catch (IOException e) {
            System.err.println("Could not save tasks: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadTasks() {
        if (!DATA_FILE.exists()) return;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(DATA_FILE))) {
            masterTaskList.addAll((List<Task>) in.readObject());
        } catch (Exception e) {
            System.err.println("Could not load tasks: " + e.getMessage());
        }
    }

    private void loadSettings() {
        if (!SETTINGS_FILE.exists()) return;
        try (FileInputStream in = new FileInputStream(SETTINGS_FILE)) {
            Properties p = new Properties();
            p.load(in);
            clockFps = Integer.parseInt(p.getProperty("clockFps", "60"));
            showMillis = Boolean.parseBoolean(p.getProperty("showMillis", "true"));
            animations = Boolean.parseBoolean(p.getProperty("animations", "true"));
            pauseInBackground = Boolean.parseBoolean(p.getProperty("pauseInBackground", "true"));
        } catch (Exception e) {
            System.err.println("Could not load settings: " + e.getMessage());
        }
    }

    private void saveSettings() {
        Properties p = new Properties();
        p.setProperty("clockFps", String.valueOf(clockFps));
        p.setProperty("showMillis", String.valueOf(showMillis));
        p.setProperty("animations", String.valueOf(animations));
        p.setProperty("pauseInBackground", String.valueOf(pauseInBackground));
        try (FileOutputStream out = new FileOutputStream(SETTINGS_FILE)) {
            p.store(out, "DayFlow Planner settings");
        } catch (IOException e) {
            System.err.println("Could not save settings: " + e.getMessage());
        }
    }
}
