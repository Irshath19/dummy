/*
*  JEditTextArea.java
*
*  � Copyright 2001-2004 Volante Technologies, Inc.
*  All rights reserved.
*
*  This software is the confidential and proprietary information of Volante
*  Technologies Inc. Your use of this software is governed by the terms of the
*  license agreement and/or any nondisclosure agreements you have entered
*  into with Volante.  This software may not be disseminated, distributed
*  or otherwise disclosed without the prior, written permission of Volante.
*/
package com.tplus.transform.swing.text;

/*
 * JEditTextArea.java - jEdit's text component
 * Copyright (C) 1999 Slava Pestov
 *
 * You may use and modify this package for any purpose. Redistribution is
 * permitted, in both source and binary form, provided that this notice
 * remains intact in all source distributions of this package.
 */

import com.tplus.transform.swing.*;
import com.tplus.transform.swing.plaf.OpaqueEmptyBorder;
import com.tplus.transform.swing.text.highlight.Highlighter;
import com.tplus.transform.swing.text.highlight.*;
import com.tplus.transform.swing.text.marker.TokenMarker;
import com.tplus.transform.swing.text.token.LineIterator;
import com.tplus.transform.swing.text.token.Token;
import com.tplus.transform.util.Location;
import com.tplus.transform.util.StringUtils;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.Timer;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.List;
import java.util.*;

/**
 * jEdit's text area component. It is more suited for editing program
 * source code than JEditorPane, because it drops the unnecessary features
 * (images, variable-width lines, and so on) and adds a whole bunch of
 * useful goodies such as:
 * <ul>
 * <li>More flexible key binding scheme
 * <li>Supports macro recorders
 * <li>Rectangular selection
 * <li>Bracket highlighting
 * <li>Syntax highlighting
 * <li>Command repetition
 * <li>Block caret can be enabled
 * </ul>
 * It is also faster and doesn't have as many problems. It can be used
 * in other applications; the only other part of jEdit it depends on is
 * the syntax package.<p>
 * <p/>
 * To use it in your app, treat it like any other component, for example:
 * <pre>JEditTextArea ta = new JEditTextArea();
 * ta.setTokenMarker(new JavaTokenMarker());
 * ta.setText("public class Test {\n"
 *     + "    public static void cmtmain(String[] args) {\n"
 *     + "        System.out.println(\"Hello World\");\n"
 *     + "    }\n"
 *     + "}");</pre>
 *
 * @author Slava Pestov
 * @version $Id: JEditTextArea.java,v 1.36 1999/12/13 03:40:30 sp Exp $
 */
public class JEditTextArea extends JComponent implements TextEditComponent, LineIterator, Accessible {
    public static final int ctrlMask = SwingUtils.CTRL_MASK;
    public static final String LF = "\n";
    public static final String CRLF = "\r\n";
    public static final String DEFAULT_LINE_SEPARATOR = LF;
    /**
     * Adding components with this name to the text area will place
     * them left of the horizontal scroll bar. In jEdit, the status
     * bar is added this way.
     */


    public static String LEFT_OF_SCROLLBAR = "los";
    public static String LEFT_OF_PANE = "lop";
    public static String RIGHT_OF_PANE = "rop";
    private String lineSeparator = DEFAULT_LINE_SEPARATOR;

    ExtendedAction copyAction = new ExtendedAction("Copy", "/images/copy.png") {
        {
            setHelpId("Edit Operations");
        }

        public void actionPerformed(ActionEvent actionEvent) {
            copy();
        }
    };
    ExtendedAction pasteAction = new ExtendedAction("Paste", "/images/paste.png") {
        public void actionPerformed(ActionEvent actionEvent) {
            paste();
        }
    };
    ExtendedAction cutAction = new ExtendedAction("Cut", "/images/cut.png") {
        {
            setHelpId("Edit Operations");
        }

        public void actionPerformed(ActionEvent actionEvent) {
            cut();
        }
    };
    ExtendedAction undoAction = new ExtendedAction("Undo","/images/undo.png") {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            undo();
        }
        {
            setAccelerator("ctrl Z");
            setHelpId("Edit Operations");
            setDescription("Undo previous action");
        }
    };
    ExtendedAction redoAction = new ExtendedAction("Redo","/images/redo.png") {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            redo();
        }
        {
            setHelpId("Edit Operations");
            setAccelerator("ctrl Y");
            setDescription("Redo the previous undone action");
        }
    };
    ExtendedAction copyAsHTMLAction = new ExtendedAction("Copy as HTML", "/images/copy.png") {
        {
            setHelpId("Edit Operations");
        }

        public void actionPerformed(ActionEvent actionEvent) {
            copyAsHTML();
        }
    };
    ExtendedAction findAction = new ExtendedAction("Find ...", "/images/find.png") {
        {
            setHelpId("Edit Operations");
        }

        public void actionPerformed(ActionEvent actionEvent) {
            onFind();
        }
    };
    ExtendedAction findNextAction = new ExtendedAction("Find Next", "/images/findagain.png") {
        {
            setHelpId("Edit Operations");
        }

        public void actionPerformed(ActionEvent actionEvent) {
            onFindNext();
        }
    };
    ExtendedAction replaceAction = new ExtendedAction("Replace ...", "/images/replace.png") {
        {
            setHelpId("Edit Operations");
        }

        public void actionPerformed(ActionEvent actionEvent) {
            onReplace();
        }
    };
    ExtendedAction goToLineAction = new ExtendedAction("Go To ...", "/images/find.png") {
        {
            setHelpId("Edit Operations");
        }

        public void actionPerformed(ActionEvent actionEvent) {
            onGoTo();
        }
    };
    ExtendedAction showLineNumbersAction = new ExtendedAction("Show Line Numbers", "/images/paste.png") {
        {
            setHelpId("Edit Operations");
        }

        public void actionPerformed(ActionEvent actionEvent) {
            boolean linesVisible = isLineNumbersVisible();
            boolean show = !linesVisible;
            showLineNumbers(show);
        }
    };
    private boolean scrollbarsHidden;
    private boolean useGlobalFind = true;
    //CORE-4456 dragActive will be true while mouse is dragged
    boolean dragActive = false;
    //CORE-7708 anchorLocation will get the updated cursor location while expanding/shrinking code selection
    protected Location anchorLocation = null;

    /**
     * Creates a new JEditTextArea with the default settings.
     */
    public JEditTextArea() {
        this(true);
    }

    public JEditTextArea(boolean dragDrop) {
        this(TextAreaDefaults.getDefaults());
    }

    TransferHandler transferHandler = new TransferHandler("selectedText");


    /**
     * Creates a new JEditTextArea with the specified settings.
     *
     * @param defaults The default settings
     */
    public JEditTextArea(TextAreaDefaults defaults) {
        // Enable the necessary events
        enableEvents(AWTEvent.KEY_EVENT_MASK);
        setDoubleBuffered(true);

        // Initialize some misc. stuff
        painter = new TextAreaPainter(this, defaults);
        //if (dragDrop)
        //     setTransferHandler(transferHandler);
        documentHandler = new DocumentHandler();
        listenerList = new EventListenerList();
        caretEvent = new MutableCaretEvent();
        lineSegment = new Segment();
        bracketLine = bracketPosition = -1;
        blink = true;
        // Initialize the GUI
        setLayout(new ScrollLayout());
        add(CENTER, painter);
        class JScrollBarExt extends JScrollBar {
            public JScrollBarExt(int orientation) {
                super(orientation);
            }

            public Dimension getPreferredSize() {
                if (isVisible()) {
                    return super.getPreferredSize();
                }
                else {
                    return new Dimension(0, 0);
                }
            }
        }
        add(RIGHT, vertical = new JScrollBarExt(JScrollBar.VERTICAL));
        add(BOTTOM, horizontal = new JScrollBarExt(JScrollBar.HORIZONTAL));
        // Add some event listeners
        vertical.addAdjustmentListener(new AdjustHandler());
        horizontal.addAdjustmentListener(new AdjustHandler());
        marginPanel.setPreferredSize(new Dimension(4, 10));
        marginPanel.setBackground(Color.white);
        marginPanel.setForeground(Color.white);
        marginPanel.setOpaque(true);
        add(LEFT_OF_PANE, marginPanel);
        //setLayout(new BorderLayout());
        //add(painter, BorderLayout.CENTER);

        painter.addComponentListener(new ComponentHandler());
        //CORE-4456 Provided accessibility support for JEditTextArea
        MouseHandler mouseHandler = new MouseHandler();
        painter.addMouseListener(mouseHandler);
        enableMouseWheel();
        painter.addMouseMotionListener(mouseHandler);
        addFocusListener(new FocusHandler());
        //Tell Accessibility Context that some caret event like Navigation, Typing has happened.
        addCaretListener(new CaretListener() {
            @Override
            public void caretUpdate(CaretEvent e) {
                onCaretUpdate();
            }
        });

        // Load the defaults
        setInputHandler(TextAreaDefaults.createDefaultInputHandler());
        setDocument(new SyntaxDocument());
        editable = defaults.editable;
        caretVisible = defaults.caretVisible;
        caretBlinks = defaults.caretBlinks;
        electricScroll = defaults.electricScroll;

        //popup = defaults.popup;

        // We don't seem to get the initial focus event?
        setFocusedComponent(this);
        new TextEditComponentFindAction().install(this);
        addContextAction(cutAction);
        addContextAction(copyAction);
        addContextAction(copyAsHTMLAction);
        addContextAction(pasteAction);
        addContextAction(undoAction);
        addContextAction(redoAction);
        addContextAction(null);
        addContextAction(findAction);
        addContextAction(findNextAction);
        addContextAction(replaceAction);
        addContextAction(null);
        addContextAction(goToLineAction);
        addContextAction(null);
        showLineNumbersAction.setName(isLineNumbersVisible() ? "Hide Line Numbers" : "Show Line Numbers");
        addContextAction(showLineNumbersAction);
        {
            ActionMap actionMap = getPainter().getActionMap();
            actionMap.put("copy", copyAction);
            actionMap.put("paste", pasteAction);
        }
        {
            ActionMap actionMap = getActionMap();
            actionMap.put("copy", copyAction);
            actionMap.put("paste", pasteAction);
        }
        //setBorder(new CompoundBorder(getScollpaneBorder(), new EmptyBorder(1, 1, 1, 1)));
        setBorder(new CompoundBorder(getScrollPaneBorder(), new OpaqueEmptyBorder(1, 0, 0, 0)));
        //CORE-1270 : 'High Contrast' Theme support.
        // get colors from UIManager
        setBackground(UIManager.getColor("Window.background"));
        painter.setBackground(UIManager.getColor("Window.background"));
        setOpaque(true);
        getPainter().addCustomHighlight(locationHighlight);

    }

    //Last spoken line is captured in order to avoid repeating the same line
    private int spokenLine = -1;

    //CORE-4456 Provided accessibility support for

    /**
     * Reads the selected text in the text area if any, else read the current line or next letter to cursor point.
     */
    private void onCaretUpdate() {
        if (!dragActive) {
            String selectedText = getSelectedText();
            String toSpeak;
            if (StringUtils.isNotEmpty(selectedText)) {
                toSpeak = selectedText;
            } else {
                int caretLine = getCaretLine();
                toSpeak = spokenLine == caretLine ? getText(getCaretPosition(), 1) : getLineText(caretLine);
                spokenLine = caretLine;
                anchorLocation = null;
            }
            AccessibleContext accessibleContext = getAccessibleContext();
            if (accessibleContext != null) {
                accessibleContext.setAccessibleName(toSpeak);
            }
        }
    }

    public void setScrollBarOnLeft(boolean scrollbarOnLeft) {
        remove(vertical);
        add(scrollbarOnLeft ? LEFT : RIGHT, vertical);
    }

    JPanel marginPanel = new JPanel() {
        public void paint(Graphics g) {
            setBackground(painter.getPaintingBackground());
            super.paint(g);
        }
    };
    LineGutter lineGutter = null;
    IconGutter iconGutter = null;

    public void addLineDisplayPanel() {
        if (lineGutter == null) {
            removeLeftOfPaneComponents();
            lineGutter = new LineGutter(this);
            addLeftOfPaneComponents();
        }
    }

    public void removeLineDisplayPanel() {
        remove(lineGutter);
        lineGutter = null;
        validate();
        repaint();
    }

    public void addRightOfPaneComponent(JComponent cmp) {
        add(RIGHT_OF_PANE, cmp);
    }

    private void removeLeftOfPaneComponents() {
        if (lineGutter != null) {
            remove(lineGutter);
        }
        remove(marginPanel);
        if (iconGutter != null) {
            remove(iconGutter);
        }
    }

    private void addLeftOfPaneComponents() {
        if (lineGutter != null) {
            add(LEFT_OF_PANE, lineGutter);
        }
        if (iconGutter != null) {
            add(LEFT_OF_PANE, iconGutter);
        }
        add(LEFT_OF_PANE, marginPanel);
        validate();
        repaint();
    }


    public void addIconDisplayPanel(LineGutterModel lineGutterModel) {
        //if (iconGutter == null) 
        {
            removeLeftOfPaneComponents();
            iconGutter = new IconGutter(this, lineGutterModel);
            addLeftOfPaneComponents();
        }
    }

    public void showIconGutter(boolean bl) {
        if (iconGutter != null) {
            iconGutter.setVisible(bl);
        }
    }

    public void removeIconDisplayPanel() {
        remove(iconGutter);
        iconGutter = null;
    }

    ComponentListener componentListener;

    public synchronized void addViewChangeListener(ComponentListener l) {
        componentListener = AWTEventMulticaster.add(componentListener, l);
    }

    public synchronized void removeComponentListener(ComponentListener l) {
        if (l == null) {
            return;
        }
        componentListener = AWTEventMulticaster.remove(componentListener, l);
    }

    void fireViewChange() {
        EventListener[] listeners = AWTEventMulticaster.getListeners(componentListener, ComponentListener.class);
        ComponentEvent event = new ComponentEvent(this, 100);
        for (EventListener listener1 : listeners) {
            ComponentListener listener = (ComponentListener) listener1;
            listener.componentResized(event);
        }
    }

    public void setBorder(Border border) {
        super.setBorder(border);
    }

    private Border getScrollPaneBorder() {
        Border border = UIManager.getBorder("ScrollPane.border");
        if (border instanceof LineBorder) {
            return border;
        }
        return new LineBorder(Color.gray);
    }

    protected void hideScrollBars(boolean hideBorders) {
        scrollbarsHidden = true;
        if (horizontal != null) {
            horizontal.setVisible(false);
            horizontal.setPreferredSize(new Dimension(0, 0));
        }
        if (vertical != null) {
            vertical.setVisible(false);
            vertical.setPreferredSize(new Dimension(0, 0));
        }
        if (hideBorders) {
            setBorder(null);
        }
    }

    public void hideScrollBars() {
        hideScrollBars(false);
    }

    public void hideScrollBarsAndBorder() {
        hideScrollBars(true);
    }

    public Insets getMargin() {
        return painter.getMargin();
    }

    public void setMargin(Insets margin) {
        painter.setMargin(margin);
    }

    public int getRows() {
        return painter.getRows();
    }

    public void setRows(int rows) {
        painter.setRows(rows);
    }

    public int getColumns() {
        return painter.getColumns();
    }

    public void setColumns(int cols) {
        painter.setColumns(cols);
    }

    private void enableMouseWheel() {
        try {
            final TextAreaPainter textAreaPainter = getPainter();
            textAreaPainter.addMouseWheelListener(new MouseWheelListener() {
                public void mouseWheelMoved(MouseWheelEvent e) {
                    if (e.isControlDown()) {
                        Font font1 = textAreaPainter.getFont();
                        float currentSize = (float) font1.getSize();
                        if (e.getWheelRotation() > 0) {
                            if (currentSize > 2) {
                                textAreaPainter.setFont(font1.deriveFont(currentSize + SwingUtils.WHEEL_DOWN_FONT_INCREMENT));
                            }
                        }
                        else {
                            if (currentSize < 50) {
                                textAreaPainter.setFont(font1.deriveFont(currentSize + SwingUtils.WHEEL_UP_FONT_INCREMENT));
                            }
                        }
                        e.consume();
                    }
                }
            });
            enableMouseWheel0();
        }
        catch (Throwable e) {

        }
    }

    private void enableMouseWheel0() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        //Class cls = Class.forName("com.tplus.transform.swing.text.WheelHandler");
        //Constructor constructor = cls.getConstructor(new Class[]{JEditTextArea.class});
        //constructor.newInstance(new Object[]{this});
        new WheelHandler(this);
    }

    public void setTabSize(int size) {
        Document doc = getDocument();
        if (doc != null) {
            int old = getTabSize();
            doc.putProperty(PlainDocument.tabSizeAttribute, size);
            firePropertyChange("tabSize", old, size);
        }
    }

    public int getTabSize() {
        int size = 8;
        Document doc = getDocument();
        if (doc != null) {
            Integer i = (Integer) doc.getProperty(PlainDocument.tabSizeAttribute);
            if (i != null) {
                size = i;
            }
        }
        return size;
    }

    /**
     * Returns if this component can be traversed by pressing
     * the Tab key. This returns false.
     */
    public final boolean isManagingFocus() {
        return true;
    }


    /**
     * Returns the object responsible for painting this text area.
     */
    public final TextAreaPainter getPainter() {
        return painter;
    }

    public Dimension getPreferredSize() {
        return super.getPreferredSize();
    }

    /**
     * Returns the input handler.
     */
    public final InputHandler getInputHandler() {
        return inputHandler;
    }

    /**
     * Sets the input handler.
     *
     * @param inputHandler The new input handler
     */
    public void setInputHandler(InputHandler inputHandler) {
        this.inputHandler = inputHandler;
    }

    /**
     * Returns true if the caret is blinking, false otherwise.
     */
    public final boolean isCaretBlinkEnabled() {
        return caretBlinks;
    }

    /**
     * Toggles caret blinking.
     *
     * @param caretBlinks True if the caret should blink, false otherwise
     */
    public void setCaretBlinkEnabled(boolean caretBlinks) {
        this.caretBlinks = caretBlinks;
        if (!caretBlinks) {
            blink = false;
        }

        painter.invalidateSelectedLines();
    }

    /**
     * Returns true if the caret is visible, false otherwise.
     */
    public final boolean isCaretVisible() {
        return (!caretBlinks || blink) && caretVisible;
    }

    /**
     * Sets if the caret should be visible.
     *
     * @param caretVisible True if the caret should be visible, false
     *                     otherwise
     */
    public void setCaretVisible(boolean caretVisible) {
        this.caretVisible = caretVisible;
        blink = true;

        painter.invalidateSelectedLines();
    }

    /**
     * Blinks the caret.
     */
    public final void blinkCaret() {
        if (caretBlinks) {
            blink = !blink;
            painter.invalidateSelectedLines();
        }
        else {
            blink = true;
        }
    }

    /**
     * Returns the number of lines from the top and button of the
     * text area that are always visible.
     */
    public final int getElectricScroll() {
        return electricScroll;
    }

    /**
     * Sets the number of lines from the top and bottom of the text
     * area that are always visible
     *
     * @param electricScroll The number of lines always visible from
     *                       the top or bottom
     */
    public final void setElectricScroll(int electricScroll) {
        this.electricScroll = electricScroll;
    }

    /**
     * Updates the state of the scroll bars. This should be called
     * if the number of lines in the document changes, or when the
     * size of the text are changes.
     */
    public void updateScrollBars() {
        fireViewChange();
        if (vertical != null && visibleLines != 0) {
            if (vertical != null && visibleLines != 0) {
                vertical.setValues(firstLine, visibleLines, 0, getLineCount());
                vertical.setUnitIncrement(2);
                vertical.setBlockIncrement(visibleLines);
                if (!scrollbarsHidden) {
                    boolean visible = visibleLines < getLineCount();
                    if (vertical.isVisible() != visible) {
                        vertical.setVisible(visible);
                        invalidate();
                        revalidate();
                    }
                    //vertical.setPreferredSize(new Dimension(visible ? 16 : 0, 40));
                }
                scrollBarsInitialized = true;
            }

            int width = painter.getWidth();
            if (horizontal != null && width != 0) {
                int maxWidth = getMaximumLineLength();
                int newMax = Math.max(maxWidth, width);
                horizontal.setValues(-horizontalOffset, width, 0, newMax);
                horizontal.setUnitIncrement(painter.getFontMetrics().charWidth('w'));
                horizontal.setBlockIncrement(width / 2);
                if (!scrollbarsHidden) {
                    boolean visible = (width) < newMax;
                    if (horizontal.isVisible() != visible) {
                        horizontal.setVisible(visible);
                        invalidate();
                        revalidate();
                    }
                    //horizontal.setPreferredSize(new Dimension(40, visible ? 16 : 0));
                }
            }
        }
    }

    public int getMaximumLineLength() {
        int maxLengthLine = getMaxLengthLine();
        int start = getLineStartOffset(maxLengthLine);
        int end = getLineEndOffset(maxLengthLine);
        int length = end - start;
        int width = offsetToX(maxLengthLine, length) + (-horizontalOffset);
        return width;
    }

    protected int getColumnWidth() {
        FontMetrics metrics = getPainter().getFontMetrics(getFont());
        int columnWidth = metrics.charWidth('m');
        return columnWidth;
    }

    protected int getRowHeight() {
        FontMetrics metrics = getPainter().getFontMetrics(getFont());
        int rowHeight = metrics.getHeight();
        return rowHeight;
    }


    /**
     * Returns the line displayed at the text area's origin.
     */
    public final int getFirstLine() {
        return firstLine;
    }

    public final int getFirstVisibleLine() {

        return firstLine;
    }

    /**
     * Sets the line displayed at the text area's origin without
     * updating the scroll bars.
     */
    public void setFirstLine(int firstLine) {
        if (firstLine == this.firstLine) {
            return;
        }
        int oldFirstLine = this.firstLine;
        if (firstLine < 0) {
            firstLine = 0;
        }
        this.firstLine = firstLine;
        if (vertical != null && firstLine != vertical.getValue()) {
            updateScrollBars();
        }
        painter.repaint();
        fireViewChange();
    }

    /**
     * Returns the number of lines visible in this text area.
     */
    public final int getVisibleLines() {
        return visibleLines;
    }

    /**
     * Recalculates the number of visible lines. This should not
     * be called directly.
     */
    public final void recalculateVisibleLines() {
        if (painter == null) {
            return;
        }
        int height = painter.getHeight();
        int lineHeight = getLineHeight();
        int oldVisibleLines = visibleLines;
        visibleLines = height / lineHeight;
        updateScrollBars();
    }

    public int getLineHeight() {
        return painter.getFontMetrics().getFontHeight();
    }

    /**
     * Returns the horizontal offset of drawn lines.
     */
    public final int getHorizontalOffset() {
        return horizontalOffset;
    }

    /**
     * Sets the horizontal offset of drawn lines. This can be used to
     * implement horizontal scrolling.
     *
     * @param horizontalOffset offset The new horizontal offset
     */
    public void setHorizontalOffset(int horizontalOffset) {
        if (horizontalOffset == this.horizontalOffset) {
            return;
        }
        this.horizontalOffset = horizontalOffset;
        if (horizontal != null && horizontalOffset != horizontal.getValue()) {
            updateScrollBars();
        }
        painter.repaint();
    }

    /**
     * A fast way of changing both the first line and horizontal
     * offset.
     *
     * @param firstLine        The new first line
     * @param horizontalOffset The new horizontal offset
     * @return True if any of the values were changed, false otherwise
     */
    public boolean setOrigin(int firstLine, int horizontalOffset) {
        boolean changed = false;
        int oldFirstLine = this.firstLine;

        if (horizontalOffset != this.horizontalOffset) {
            this.horizontalOffset = horizontalOffset;
            changed = true;
        }

        if (firstLine != this.firstLine) {
            this.firstLine = firstLine;
            changed = true;
        }

        if (changed) {
            updateScrollBars();
            painter.repaint();
            //fireViewChange();
        }

        return changed;
    }

    /**
     * Ensures that the caret is visible by scrolling the text area if
     * necessary.
     *
     * @return True if scrolling was actually performed, false if the
     * caret was already visible
     */
    public boolean scrollToCaret() {
        int line = getCaretLine();
        int lineStart = getLineStartOffset(line);
        int offset = Math.max(0, Math.min(getLineLength(line) - 1,
                getCaretPosition() - lineStart));

        return scrollTo(line, offset);
    }

    /**
     * Ensures that the specified line and offset is visible by scrolling
     * the text area if necessary.
     *
     * @param line   The line to scroll to
     * @param offset The offset in the line to scroll to
     * @return True if scrolling was actually performed, false if the
     * line and offset was already visible
     */
    public boolean scrollTo(int line, int offset) {
        // visibleLines == 0 before the component is realized
        // we can't do any proper scrolling then, so we have
        // this hack...
        if (visibleLines == 0) {
            setFirstLine(Math.max(0, line - electricScroll));
            return true;
        }

        int newFirstLine = firstLine;
        int newHorizontalOffset = horizontalOffset;

        if (line < firstLine + electricScroll) {
            newFirstLine = Math.max(0, line - electricScroll);
        }
        else if (line + electricScroll >= firstLine + visibleLines) {
            newFirstLine = (line - visibleLines) + electricScroll + 1;
            if (newFirstLine + visibleLines >= getLineCount()) {
                newFirstLine = getLineCount() - visibleLines;
            }
            if (newFirstLine < 0) {
                newFirstLine = 0;
            }
        }

        int x = _offsetToX(line, offset);
        int width = painter.getFontMetrics().charWidth('w');

        if (x < 0) {
            newHorizontalOffset = Math.min(0, horizontalOffset
                    - x + width + 5);
        }
        else if (x + width >= painter.getWidth()) {
            newHorizontalOffset = horizontalOffset +
                    (painter.getWidth() - x) - width - 15;
        }

        return setOrigin(newFirstLine, newHorizontalOffset);
    }

    /**
     * Converts a line index to a y co-ordinate.
     *
     * @param line The line
     */
    public int lineToY(int line) {
        FontMetrics2 fm = painter.getFontMetrics();
        return (line - firstLine) * fm.getFontHeight()
                - (fm.getLeading() + fm.getMaxDescent());
    }

    public void grabNextKeyStroke(KeyListener grabListener) {
        getInputHandler().grabNextKeyStroke(grabListener);
    }

    public void setFont(Font font) {
        super.setFont(font);
        painter.setFont(font);
    }

    /**
     * Converts a y co-ordinate to a line index.
     *
     * @param y The y co-ordinate
     */
    public int yToLine(int y) {
        FontMetrics2 fm = painter.getFontMetrics();
        int height = fm.getFontHeight();
        int line = (y) / height;
        line = Math.max(0, Math.min(getLineCount() - 1, line + firstLine));
        return line;
    }

    /**
     * Converts an offset in a line into an x co-ordinate. This is a
     * slow version that can be used any time.
     *
     * @param line   The line
     * @param offset The offset, from the start of the line
     */
    public final int offsetToX(int line, int offset) {
        // don't use cached tokens
        painter.currentLineTokens = null;
        return _offsetToX(line, offset);
    }

    /**
     * Converts an offset in a line into an x co-ordinate. This is a
     * fast version that should only be used if no changes were made
     * to the text since the last repaint.
     *
     * @param line   The line
     * @param offset The offset, from the start of the line
     */
    public int _offsetToX(int line, int offset) {
        TokenMarker tokenMarker = getTokenMarker();

        /* Use painter's cached info for speed */

        getLineText(line, lineSegment);

        int segmentOffset = lineSegment.offset;
        int x = horizontalOffset;

        /* If syntax coloring is disabled, do simple translation */
        if (tokenMarker == null) {
            //FontMetrics2 fm = painter.getFontMetrics();
            LogicalFont logicalFont = painter.getLogicalFont();
            lineSegment.count = offset;
            return x + SyntaxUtilities.getTabbedTextWidth(lineSegment, logicalFont, x, painter, 0, painter.getSpecialCharPainter());
        }
        /* If syntax coloring is enabled, we have to do this because
         * tokens can vary in width */
        else {
            Token tokens;
            if (painter.currentLineIndex == line
                    && painter.currentLineTokens != null) {
                tokens = painter.currentLineTokens;
            }
            else {
                painter.currentLineIndex = line;
                tokens = painter.currentLineTokens
                        = tokenMarker.markTokens(SegmentLine.create(lineSegment), line);
            }

            Toolkit toolkit = painter.getToolkit();
            //Font defaultFont = painter.getFont();
            LogicalFont defaultFont = painter.getLogicalFont();
            SyntaxStyle[] styles = painter.getStyles();

            for (; ; ) {
                byte id = tokens.id;
                if (id == Token.END) {
                    return x;
                }
                //FontMetrics2 fm;
                LogicalFont currentFont;
                if (id == Token.NULL) {
                    //fm = painter.getFontMetrics();
                    currentFont = defaultFont;
                }
                else {
                    //fm = styles[id].getFontMetrics(defaultFont, painter.getInterLineSpacing());
                    currentFont = styles[id].getStyledLogicalFont(defaultFont);
                }

                int length = tokens.length;

                if (offset + segmentOffset < lineSegment.offset + length) {
                    lineSegment.count = offset - (lineSegment.offset - segmentOffset);
                    return x + SyntaxUtilities.getTabbedTextWidth(lineSegment, currentFont, x, painter, 0, painter.getSpecialCharPainter());
                }
                else {
                    lineSegment.count = length;
                    x += SyntaxUtilities.getTabbedTextWidth(lineSegment, currentFont, x, painter, 0, painter.getSpecialCharPainter());
                    lineSegment.offset += length;
                }
                tokens = tokens.next;
            }
        }
    }

    public int getOffsetFromLocation(int line, int column) {
        return getLineStartOffset(line) + column;
    }

    /**
     * Converts an x co-ordinate to an offset within a line.
     *
     * @param line The line
     * @param x    The x co-ordinate
     */
    public int xToOffset(int line, int x) {
        TokenMarker tokenMarker = getTokenMarker();

        /* Use painter's cached info for speed */
        //FontMetrics2 fm = painter.getFontMetrics();
        LogicalFont defaultLogicalFont = painter.getLogicalFont();
        LogicalFont currentFont = defaultLogicalFont;

        getLineText(line, lineSegment);

        char[] segmentArray = lineSegment.array;
        int segmentOffset = lineSegment.offset;
        int segmentCount = lineSegment.count;

        int width = horizontalOffset;
        SyntaxUtilities.SpecialCharPainter specialCharPainter = painter.getSpecialCharPainter();
        if (tokenMarker == null) {
            for (int i = 0; i < segmentCount; i++) {
                char c = segmentArray[i + segmentOffset];
                int charWidth;
                if (c == '\t') {
                    charWidth = (int) painter.nextTabStop(width, i) - width;
                }
                else {
                    charWidth = SyntaxUtilities.getCharWidth(c, currentFont, specialCharPainter);
                }

                if (painter.isBlockCaretEnabled()) {
                    if (x - charWidth <= width) {
                        return i;
                    }
                }
                else {
                    if (x - charWidth / 2 <= width) {
                        return i;
                    }
                }

                width += charWidth;
            }

            return segmentCount;
        }
        else {
            Token tokens;
            if (painter.currentLineIndex == line && painter
                    .currentLineTokens != null) {
                tokens = painter.currentLineTokens;
            }
            else {
                painter.currentLineIndex = line;
                painter.currentLineTokens = tokenMarker.markTokens(SegmentLine.create(lineSegment), line);
                tokens = painter.currentLineTokens;
            }

            int offset = 0;
            Toolkit toolkit = painter.getToolkit();
            //Font defaultFont = painter.getFont();
            //LogicalFont defaultFont = painter.getLogicalFont();
            SyntaxStyle[] styles = painter.getStyles();

            for (; ; ) {
                byte id = tokens.id;
                if (id == Token.END) {
                    return offset;
                }

                if (id == Token.NULL) {
                    currentFont = painter.getLogicalFont();
                }
                else {
                    currentFont = styles[id].getStyledLogicalFont(defaultLogicalFont);
                }

                int length = tokens.length;

                for (int i = 0; i < length; i++) {
                    int charOffset = segmentOffset + offset + i;
                    if (charOffset >= segmentArray.length) {
                        break;
                    }
                    char c = segmentArray[charOffset];
                    int charWidth;
                    if (c == '\t') {
                        charWidth = (int) painter.nextTabStop(width, offset + i) - width;
                    }
                    else {
                        charWidth = SyntaxUtilities.getCharWidth(c, currentFont, specialCharPainter);
                    }
                    //charWidth = fm.charWidth(c);

                    if (painter.isBlockCaretEnabled()) {
                        if (x - charWidth <= width) {
                            return offset + i;
                        }
                    }
                    else {
                        if (x - charWidth / 2 <= width) {
                            return offset + i;
                        }
                    }

                    width += charWidth;
                }

                offset += length;
                tokens = tokens.next;
            }
        }
    }

    /**
     * Converts a point to an offset, from the start of the text.
     *
     * @param x The x co-ordinate of the point
     * @param y The y co-ordinate of the point
     */
    public int xyToOffset(int x, int y) {
        int line = yToLine(y);
        int start = getLineStartOffset(line);
        return start + xToOffset(line, x);
    }

    /**
     * Returns the document this text area is editing.
     */
    public final SyntaxDocument getSyntaxDocument() {
        return document;
    }

    public final Document getDocument() {
        return document;
    }

    public JComponent getJComponent() {
        return this;
    }

    /**
     * Sets the document this text area is editing.
     *
     * @param document The document
     */
    public void setDocument(SyntaxDocument document) {
        if (this.document == document) {
            return;
        }
        if (this.document != null) {
            this.document.removeDocumentListener(documentHandler);
            if (undoManager != null) {
                this.document.removeUndoableEditListener(undoManager);
            }
        }

        this.document = document;

        document.addDocumentListener(documentHandler);
        undoManager = new UndoManager();
        document.addUndoableEditListener(undoManager);

        select(0, 0);
        updateScrollBars();
        painter.repaint();
    }

    public void undo() {
        if (undoManager != null) {
            try {
                undoManager.undo();
            }
            catch (CannotUndoException e) {
                getToolkit().beep();
            }
        }
    }

    public void redo() {
        if (undoManager != null) {
            try {
                undoManager.redo();
            }
            catch (CannotRedoException e) {
                getToolkit().beep();
            }
        }
    }

    /**
     * Returns the document's token marker. Equivalent to calling
     * <code>getDocument().getTokenMarker()</code>.
     */
    public final TokenMarker getTokenMarker() {
        return document.getTokenMarker();
    }

    /**
     * Sets the document's token marker. Equivalent to caling
     * <code>getDocument().setTokenMarker()</code>.
     *
     * @param tokenMarker The token marker
     */
    public final void setTokenMarker(TokenMarker tokenMarker) {
        document.setTokenMarker(tokenMarker);
        if (tokenMarker != null) {
            SyntaxStyle[] syntaxStyles = tokenMarker.getSyntaxStyles();
            fixStyles(syntaxStyles);
            painter.setStyles(syntaxStyles);
        }
    }

    private void fixStyles(SyntaxStyle[] syntaxStyles) {
        for (int i = 0; i < syntaxStyles.length; i++) {
            SyntaxStyle syntaxStyle = syntaxStyles[i];
            if (syntaxStyle == null) {
                syntaxStyles[i] = syntaxStyles[Token.NULL];
            }
        }
    }

    /**
     * Returns the length of the document. Equivalent to calling
     * <code>getDocument().getLength()</code>.
     */
    public final int getDocumentLength() {
        return document.getLength();
    }

    /**
     * Returns the number of lines in the document.
     */
    public final int getLineCount() {
        return document.getDefaultRootElement().getElementCount();
    }

    /**
     * Returns the line containing the specified offset.
     *
     * @param offset The offset
     */
    public final int getLineOfOffset(int offset) {
        return document.getDefaultRootElement().getElementIndex(offset);
    }

    public int getOffset(int line, int column) {
        return getLineStartOffset(line) + column;
    }

    public Location getLocation(int start) {
        int startLine = getLineOfOffset(start);
        int startOffset = start - getLineStartOffset(startLine);
        Location startLocation = new Location(startLine + 1, startOffset + 1);
        return startLocation;
    }

    public int getOffset(Location location) {
        return getOffset(location.getLine() - 1, location.getColumn() - 1);
    }

    public String getLine(int line) {
        int lineStartOffset = getLineStartOffset(line);
        int lineEndOffset = getLineEndOffset(line);
        if (lineStartOffset >= 0) {
            return getText(lineStartOffset, lineEndOffset - line);
        }
        return null;
    }

    /**
     * Returns the start offset of the specified line.
     *
     * @param line The line
     * @return The start offset of the specified line, or -1 if the line is
     * invalid
     */
    public int getLineStartOffset(int line) {
        Element rootElement = document.getDefaultRootElement();
        if (line >= 0 && line < rootElement.getElementCount()) {
            Element lineElement = rootElement.getElement(line);
            if (lineElement == null) {
                return -1;
            }
            else {
                return lineElement.getStartOffset();
            }
        }
        return -1;
    }

    public int getMaxColumn() {
        int maxLength = 0;
        Element rootElement = document.getDefaultRootElement();
        int elementCount = rootElement.getElementCount();
        for (int i = 0; i < elementCount; ++i) {
            Element elm = rootElement.getElement(i);
            int length = elm.getEndOffset() - elm.getStartOffset();
            maxLength = Math.max(maxLength, length);
        }
        return maxLength;
    }

    public int getMaxLengthLine() {
        int maxLength = 0;
        int maxLengthLine = 0;
        Element rootElement = document.getDefaultRootElement();
        for (int i = 0; i < rootElement.getElementCount(); ++i) {
            Element elm = rootElement.getElement(i);
            int length = elm.getEndOffset() - elm.getStartOffset();
            if (maxLength < length) {
                maxLength = length;
                maxLengthLine = i;
            }
        }
        return maxLengthLine;
    }


    /**
     * Returns the end offset of the specified line.
     *
     * @param line The line
     * @return The end offset of the specified line, or -1 if the line is
     * invalid.
     */
    public int getLineEndOffset(int line) {
        Element lineElement = document.getDefaultRootElement()
                .getElement(line);
        if (lineElement == null) {
            return -1;
        }
        else {
            return lineElement.getEndOffset();
        }
    }

    /**
     * Returns the length of the specified line.
     *
     * @param line The line
     */
    public int getLineLength(int line) {
        Element lineElement = document.getDefaultRootElement()
                .getElement(line);
        if (lineElement == null) {
            return -1;
        }
        else {
            return lineElement.getEndOffset()
                    - lineElement.getStartOffset() - 1;
        }
    }

    /**
     * Returns the entire text of this text area.
     */
    public String getText() {
        try {
            return document.getText(0, document.getLength());
        }
        catch (BadLocationException bl) {
            bl.printStackTrace();
            return null;
        }
    }

    /**
     * Sets the entire text of this text area.
     */
    public void setText(String text) {
        try {
            undoManager.discardAllEdits();
            document.beginCompoundEdit();
            document.remove(0, document.getLength());
            document.insertString(0, text, null);
            select(0, 0);
        }
        catch (BadLocationException bl) {
            bl.printStackTrace();
        }
        finally {
            document.endCompoundEdit();
        }
        undoManager.discardAllEdits();
    }

    public void discardAllEdits() {
        undoManager.discardAllEdits();
    }

    public boolean hasUndoableEdits() {
        return undoManager.canUndo();
    }

    public synchronized void addActionListener(ActionListener l) {
        listenerList.add(ActionListener.class, l);
    }

    public synchronized void removeActionListener(ActionListener l) {
        listenerList.remove(ActionListener.class, l);
    }

    protected void fireActionPerformed() {
        // Guaranteed to return a non-null array
        Object[] listeners = listenerList.getListenerList();
        ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, getText());
        // Process the listeners last to first, notifying
        // those that are interested in this event
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == ActionListener.class) {
                ((ActionListener) listeners[i + 1]).actionPerformed(e);
            }
        }
    }

    /**
     * Returns the specified substring of the document.
     *
     * @param start The start offset
     * @param len   The length of the substring
     * @return The substring, or null if the offsets are invalid
     */
    public final String getText(int start, int len) {
        try {
            return document.getText(start, len);
        }
        catch (BadLocationException bl) {
            bl.printStackTrace();
            return null;
        }
    }

    /**
     * Copies the specified substring of the document into a segment.
     * If the offsets are invalid, the segment will contain a null string.
     *
     * @param start   The start offset
     * @param len     The length of the substring
     * @param segment The segment
     */
    public final void getText(int start, int len, Segment segment) {
        try {
            document.getText(start, len, segment);
        }
        catch (BadLocationException bl) {
            bl.printStackTrace();
            segment.offset = segment.count = 0;
        }
    }

    /**
     * Returns the text on the specified line.
     *
     * @param lineIndex The line
     * @return The text, or null if the line is invalid
     */
    public final String getLineText(int lineIndex) {
        int start = getLineStartOffset(lineIndex);
        return getText(start, getLineEndOffset(lineIndex) - start - 1);
    }

    /**
     * Copies the text on the specified line into a segment. If the line
     * is invalid, the segment will contain a null string.
     *
     * @param lineIndex The line
     */
    public final void getLineText(int lineIndex, Segment segment) {
        int start = getLineStartOffset(lineIndex);
        getText(start, getLineEndOffset(lineIndex) - start - 1, segment);
    }

    /**
     * Returns the selection start offset.
     */
    public final int getSelectionStart() {
        return selectionStart;
    }


    /**
     * Returns the offset where the selection starts on the specified
     * line.
     */
    public int getSelectionStart(int line) {
        if (line == selectionStartLine) {
            return selectionStart;
        }
        else if (rectSelect) {
            Element map = document.getDefaultRootElement();
            int start = selectionStart - map.getElement(selectionStartLine)
                    .getStartOffset();

            Element lineElement = map.getElement(line);
            int lineStart = lineElement.getStartOffset();
            int lineEnd = lineElement.getEndOffset() - 1;
            return Math.min(lineEnd, lineStart + start);
        }
        else {
            return getLineStartOffset(line);
        }
    }

    /**
     * Returns the selection start line.
     */
    public final int getSelectionStartLine() {
        return selectionStartLine;
    }

    public final int getSelectionStartOffset() {
        return selectionStart - getLineStartOffset(selectionStartLine);
    }

    /**
     * Sets the selection start. The new selection will be the new
     * selection start and the old selection end.
     *
     * @param selectionStart The selection start
     * @see #select(int, int)
     */
    public final void setSelectionStart(int selectionStart) {
        select(selectionStart, selectionEnd);
    }

    /**
     * Returns the selection end offset.
     */
    public final int getSelectionEnd() {
        return selectionEnd;
    }

    /**
     * Returns the offset where the selection ends on the specified
     * line.
     */
    public int getSelectionEnd(int line) {
        if (line == selectionEndLine) {
            return selectionEnd;
        }
        else if (rectSelect) {
            Element map = document.getDefaultRootElement();
            int end = selectionEnd - map.getElement(selectionEndLine)
                    .getStartOffset();

            Element lineElement = map.getElement(line);
            int lineStart = lineElement.getStartOffset();
            int lineEnd = lineElement.getEndOffset() - 1;
            return Math.min(lineEnd, lineStart + end);
        }
        else {
            return getLineEndOffset(line) - 1;
        }
    }

    /**
     * Returns the selection end line.
     */
    public final int getSelectionEndLine() {
        return selectionEndLine;
    }

    /**
     * Sets the selection end. The new selection will be the old
     * selection start and the bew selection end.
     *
     * @param selectionEnd The selection end
     * @see #select(int, int)
     */
    public final void setSelectionEnd(int selectionEnd) {
        select(selectionStart, selectionEnd);
    }

    /**
     * Returns the caret position. This will either be the selection
     * start or the selection end, depending on which direction the
     * selection was made in.
     */
    public final int getCaretPosition() {
        return (biasLeft ? selectionStart : selectionEnd);
    }

    /**
     * Returns the caret line.
     */
    public final int getCaretLine() {
        return (biasLeft ? selectionStartLine : selectionEndLine);
    }

    public final int getCaretColumn() {
        int caretOffset = (biasLeft ? selectionStart : selectionEnd);
        int lineNo = getCaretLine();
        int lineStartOffset = getLineStartOffset(lineNo);
        int col = caretOffset - lineStartOffset;
        return col;
    }

    /**
     * Returns the mark position. This will be the opposite selection
     * bound to the caret position.
     *
     * @see #getCaretPosition()
     */
    public final int getMarkPosition() {
        return (biasLeft ? selectionEnd : selectionStart);
    }


    /**
     * Returns the mark line.
     */
    public final int getMarkLine() {
        return (biasLeft ? selectionEndLine : selectionStartLine);
    }

    /**
     * Sets the caret position. The new selection will consist of the
     * caret position only (hence no text will be selected)
     *
     * @param caret The caret position
     * @see #select(int, int)
     */
    public final void setCaretPosition(int caret) {
        select(caret, caret);
    }

    /**
     * Selects all text in the document.
     */
    public final void selectAll() {
        select(0, getDocumentLength());
    }

    /**
     * Moves the mark to the caret position.
     */
    public final void selectNone() {
        select(getCaretPosition(), getCaretPosition());
    }

    /**
     * Selects from the start offset to the end offset. This is the
     * general selection method used by all other selecting methods.
     * The caret position will be start if start &lt; end, and end
     * if end &gt; start.
     *
     * @param start The start offset
     * @param end   The end offset
     */
    public void select(int start, int end) {
        if (visibleLines == 0) {
            recalculateVisibleLines();
        }
        int newStart, newEnd;
        boolean newBias;
        if (start <= end) {
            newStart = start;
            newEnd = end;
            newBias = false;
        }
        else {
            newStart = end;
            newEnd = start;
            newBias = true;
        }

        if (newEnd > getDocumentLength()) {
            newEnd = getDocumentLength();
        }
        if (newStart > getDocumentLength()) {
            newStart = 0;
        }
        if (newStart < 0 || newEnd > getDocumentLength()) {
            throw new IllegalArgumentException("Bounds out of"
                    + " range: " + newStart + "," +
                    newEnd);
        }

        // If the new position is the same as the old, we don't
        // do all this crap, however we still do the stuff at
        // the end (clearing magic position, scrolling)
        if (newStart != selectionStart || newEnd != selectionEnd
                || newBias != biasLeft) {
            int newStartLine = getLineOfOffset(newStart);
            int newEndLine = getLineOfOffset(newEnd);

            if (painter.isBracketHighlightEnabled()) {
                if (bracketLine != -1) {
                    painter.invalidateLine(bracketLine);
                }
                updateBracketHighlight(end);
                if (bracketLine != -1) {
                    painter.invalidateLine(bracketLine);
                }
            }

            painter.invalidateLineRange(selectionStartLine, selectionEndLine);
            painter.invalidateLineRange(newStartLine, newEndLine);

            document.addUndoableEdit(new CaretUndo(selectionStart, selectionEnd));

            selectionStart = newStart;
            selectionEnd = newEnd;
            selectionStartLine = newStartLine;
            selectionEndLine = newEndLine;
            biasLeft = newBias;

            fireCaretEvent();
        }

        // When the user is typing, etc, we don't want the caret
        // to blink
        blink = true;
        caretTimer.restart();

        // Disable rectangle select if selection start = selection end
        if (selectionStart == selectionEnd) {
            rectSelect = false;
        }

        // Clear the `magic' caret position used by up/down
        magicCaret = -1;

        scrollToCaret();
    }

    public boolean isModifiedSince(String key) {
        DocumentModificationListener documentModificationListener = (DocumentModificationListener) getClientProperty("modified." + key);
        if (documentModificationListener == null) {
            return true;
        }
        return documentModificationListener.isModified();
    }

    public void resetModifiedSince(String key) {
        DocumentModificationListener documentModificationListener = (DocumentModificationListener) getClientProperty("modified." + key);
        if (documentModificationListener == null) {
            documentModificationListener = new DocumentModificationListener();
            getDocument().addDocumentListener(documentModificationListener);
            putClientProperty("modified." + key, documentModificationListener);
        }
        documentModificationListener.resetModified();
    }

    /**
     * Returns the selected text, or null if no selection is active.
     */
    public final String getSelectedText() {
        if (selectionStart == selectionEnd) {
            return null;
        }

        if (rectSelect) {
            // Return each row of the selection on a new line

            Element map = document.getDefaultRootElement();

            int start = selectionStart - map.getElement(selectionStartLine)
                    .getStartOffset();
            int end = selectionEnd - map.getElement(selectionEndLine)
                    .getStartOffset();

            // Certain rectangles satisfy this condition...
            if (end < start) {
                int tmp = end;
                end = start;
                start = tmp;
            }

            StringBuffer buf = new StringBuffer();
            Segment seg = new Segment();

            for (int i = selectionStartLine; i <= selectionEndLine; i++) {
                Element lineElement = map.getElement(i);
                int lineStart = lineElement.getStartOffset();
                int lineEnd = lineElement.getEndOffset() - 1;
                int lineLen = lineEnd - lineStart;

                lineStart = Math.min(lineStart + start, lineEnd);
                lineLen = Math.min(end - start, lineEnd - lineStart);

                getText(lineStart, lineLen, seg);
                buf.append(seg.array, seg.offset, seg.count);

                if (i != selectionEndLine) {
                    buf.append('\n');
                }
            }

            return buf.toString();
        }
        else {
            return getText(selectionStart,
                    selectionEnd - selectionStart);
        }
    }

    /**
     * Replaces the selection with the specified text.
     *
     * @param selectedText The replacement text for the selection
     */
    public void setSelectedText(String selectedText) {
        if (!canEdit()) {
            throw new InternalError("Text component"
                    + " read only");
        }

        document.beginCompoundEdit();

        try {
            if (rectSelect) {
                Element map = document.getDefaultRootElement();

                int start = selectionStart - map.getElement(selectionStartLine)
                        .getStartOffset();
                int end = selectionEnd - map.getElement(selectionEndLine)
                        .getStartOffset();

                // Certain rectangles satisfy this condition...
                if (end < start) {
                    int tmp = end;
                    end = start;
                    start = tmp;
                }

                int lastNewline = 0;
                int currNewline = 0;

                for (int i = selectionStartLine; i <= selectionEndLine; i++) {
                    Element lineElement = map.getElement(i);
                    int lineStart = lineElement.getStartOffset();
                    int lineEnd = lineElement.getEndOffset() - 1;
                    int rectStart = Math.min(lineEnd, lineStart + start);

                    document.remove(rectStart, Math.min(lineEnd - rectStart,
                            end - start));

                    if (selectedText == null) {
                        continue;
                    }

                    currNewline = selectedText.indexOf('\n', lastNewline);
                    if (currNewline == -1) {
                        currNewline = selectedText.length();
                    }

                    document.insertString(rectStart, selectedText
                            .substring(lastNewline, currNewline), null);

                    lastNewline = Math.min(selectedText.length(),
                            currNewline + 1);
                }

                if (selectedText != null &&
                        currNewline != selectedText.length()) {
                    int offset = map.getElement(selectionEndLine)
                            .getEndOffset() - 1;
                    document.insertString(offset, "\n", null);
                    document.insertString(offset + 1, selectedText
                            .substring(currNewline + 1), null);
                }
            }
            else {
                document.remove(selectionStart,
                        selectionEnd - selectionStart);
                if (selectedText != null) {
                    if (selectionStart > document.getLength()) {
                        selectionStart = document.getLength();
                    }
                    document.insertString(selectionStart,
                            selectedText, null);
                }
            }
        }
        catch (BadLocationException bl) {
            bl.printStackTrace();
            throw new InternalError("Cannot replace"
                    + " selection at " + bl.offsetRequested() + " available " + document.getLength());
        }
        // No matter what happends... stops us from leaving document
        // in a bad state
        finally {
            document.endCompoundEdit();
        }

        setCaretPosition(selectionEnd);
    }

    public void selectWord() {
        int[] wordBounds = getCurrentWordBounds();
        select(wordBounds[0], wordBounds[1]);
    }

    public void selectWord(String noWordSep) {
        int[] wordBounds = getCurrentWordBounds(noWordSep);
        select(wordBounds[0], wordBounds[1]);
    }

    public int[] getCurrentWordBounds() {
        return getCurrentWordBounds((String) getDocument().getProperty("noWordSep"));
    }

    public int[] getCurrentWordBounds(String noWordSep) {
        int caret = getCaretPosition();
        int line = getCaretLine();
        int lineStart = getLineStartOffset(line);
        caret -= lineStart;
        int selectionStart = 0;

        String lineText = getLineText(getCaretLine());

        // is in first position? then we start where the line begins
        if (caret == 0) {
            selectionStart = lineStart;
        }
        else if (caret > 0) {
            // it is not the start, so let's look for the first character
            selectionStart = lineStart + TextUtilities.findWordStart(lineText, caret, noWordSep);
        }
        // Now the end
        if (caret < lineText.length()) {
            caret = TextUtilities.findWordEnd(lineText, caret, noWordSep);
        }
        caret += lineStart;
        return new int[]{selectionStart, caret};
    }

    public String getCurrentWord() {
        int[] wordBounds = getCurrentWordBounds();
        return this.getText(wordBounds[0], wordBounds[1] - wordBounds[0]);
    }

    public String getCurrentWord(String noWordSep) {
        int[] wordBounds = getCurrentWordBounds(noWordSep);
        return this.getText(wordBounds[0], wordBounds[1] - wordBounds[0]);
    }

    public void selectToMatchingBracket() {
        int caretPosition = getCaretPosition();
        int offset = -1;
        try {
            offset = TextUtilities.findMatchingBracket(document, caretPosition - 1);
        }
        catch (BadLocationException e) {
            e.printStackTrace();
        }
        if (offset != -1 && offset != caretPosition) {
            if (offset > caretPosition) {
                select(caretPosition - 1, offset + 1);
            }
            else {
                select(offset, caretPosition);
            }
        }
        else {
            //getToolkit().beep();
            selectBlock();
        }
    }

    /**
     * Selects the code block surrounding the caret.
     *
     * @since jEdit 2.7pre2
     */
    public void selectBlock() {
        String text = getText();
        int textLength = text.length();
        String openBrackets = "<({[";
        String closeBrackets = ">)}]";
        int start = getSelectionStart(), end = getSelectionEnd();

        // Scan backwards, trying to find a bracket
        int count = 1;
        char openBracket = '\0';
        char closeBracket = '\0';

        // We can't do the backward scan if start == 0
        if (start == 0) {
            getToolkit().beep();
            return;
        }

        backward_scan:
        while (--start > 0) {
            char c = text.charAt(start);
            int index = openBrackets.indexOf(c);
            if (index != -1) {
                if (--count == 0) {
                    openBracket = c;
                    closeBracket = closeBrackets.charAt(index);
                    break backward_scan;
                }
            }
            else if (closeBrackets.indexOf(c) != -1) {
                count++;
            }
        }

        // Reset count
        count = 1;

        // Scan forward, matching that bracket
        if (openBracket == '\0') {
            getToolkit().beep();
            return;
        }
        else {
            forward_scan:
            do {
                char c = text.charAt(end);
                if (c == closeBracket) {
                    if (--count == 0) {
                        end++;
                        break forward_scan;
                    }
                }
                else if (c == openBracket) {
                    count++;
                }
            } while (++end < textLength);
        }

        select(start, end);
    }

    public void commentLine(int line) {
        String lineText = getLineText(line);
        int lineOffset = getLineStartOffset(line);
        if (lineText.startsWith("//")) {
            select(lineOffset, lineOffset + 2);
            setSelectedText("");
        }
        else {
            setCaretPosition(lineOffset);
            setSelectedText("//");
        }
    }

    public void commentLine() {
        int selectionStartLine = getLineOfOffset(selectionStart);
        int selectionEndLine = getLineOfOffset(selectionEnd);
        if (selectionStartLine == selectionEndLine) {
            commentLine(getCaretLine());
        }
        else {
            for (int currentLine = selectionStartLine; currentLine <= selectionEndLine; currentLine++) {
                commentLine(currentLine);
            }
        }
    }

    public void indentSelectedLines() {
        int startLine = getSelectionStartLine();
        int endLine = getSelectionEndLine();
        indentLinesBetween(startLine, endLine);
    }

    public void indentLinesBetween(int startLine, int endLine) {
        for (int i = startLine; i <= endLine; i++) {
            indentLine(i);
        }
    }

    public void indentLine(int line) {
        String lineText = getLineText(line).trim();
        if (line != 0) {
            String prevLineText = getLineText(line - 1);
            String whiteSpace = TextUtilities.getLeadingWhitespace(prevLineText);
            if (!lineText.startsWith("}") && (prevLineText.endsWith("{") || prevLineText.endsWith(":"))) {
                whiteSpace += '\t';
            }
            else if (lineText.indexOf('}') != -1 && whiteSpace.length() >= 1) {
                whiteSpace = whiteSpace.substring(0, whiteSpace.length() - 1);
            }
            else if ((prevLineText.endsWith("/**") || prevLineText.endsWith("/*")) && lineText.startsWith("*")) {
                whiteSpace += " ";
            }
            else if (lineText.endsWith("*/")) {
                whiteSpace = whiteSpace.substring(0, whiteSpace.length() - 1);
            }
            lineText = whiteSpace + lineText;
        }
        selectLine(line);
        setSelectedText(lineText);
    }

    public void selectLine(int line) {
        select(getLineStartOffset(line), getLineEndOffset(line) - 1);
    }

    /**
     * Returns true if this text area is editable, false otherwise.
     */
    public final boolean isEditable() {
        return editable;
    }

    /**
     * Sets if this component is editable.
     *
     * @param editable True if this text area should be editable,
     *                 false otherwise
     */
    public final void setEditable(boolean editable) {
        this.editable = editable;
    }

    public void addContextAction(Action action) {
        actions.add(action);
    }

    public void addContextAction(int index, Action action) {
        actions.add(index, action);
    }

    public void removeContextAction(Action action) {
        actions.remove(action);
    }

    /**
     * Returns the `magic' caret position. This can be used to preserve
     * the column position when moving up and down lines.
     */
    public final int getMagicCaretPosition() {
        return magicCaret;
    }

    /**
     * Sets the `magic' caret position. This can be used to preserve
     * the column position when moving up and down lines.
     *
     * @param magicCaret The magic caret position
     */
    public final void setMagicCaretPosition(int magicCaret) {
        this.magicCaret = magicCaret;
    }

    /**
     * Similar to <code>setSelectedText()</code>, but overstrikes the
     * appropriate number of characters if overwrite mode is enabled.
     *
     * @param str The string
     * @see #setSelectedText(String)
     * @see #isOverwriteEnabled()
     */
    public void overwriteSetSelectedText(String str) {
        // Don't overstrike if there is a selection
        if (!overwrite || selectionStart != selectionEnd) {
            setSelectedText(str);
            return;
        }

        // Don't overstrike if we're on the end of
        // the line
        int caret = getCaretPosition();
        int caretLineEnd = getLineEndOffset(getCaretLine());
        if (caretLineEnd - caret <= str.length()) {
            setSelectedText(str);
            return;
        }

        document.beginCompoundEdit();

        try {
            document.remove(caret, str.length());
            document.insertString(caret, str, null);
        }
        catch (BadLocationException bl) {
            bl.printStackTrace();
        }
        finally {
            document.endCompoundEdit();
        }
    }

    /**
     * Returns true if overwrite mode is enabled, false otherwise.
     */
    public final boolean isOverwriteEnabled() {
        return overwrite;
    }

    /**
     * Sets if overwrite mode should be enabled.
     *
     * @param overwrite True if overwrite mode should be enabled,
     *                  false otherwise.
     */
    public final void setOverwriteEnabled(boolean overwrite) {
        this.overwrite = overwrite;
        painter.invalidateSelectedLines();
    }

    /**
     * Returns true if the selection is rectangular, false otherwise.
     */
    public final boolean isSelectionRectangular() {
        return rectSelect;
    }

    /**
     * Sets if the selection should be rectangular.
     *
     * @param rectSelect True if the selection should be rectangular,
     *                   false otherwise.
     */
    public final void setSelectionRectangular(boolean rectSelect) {
        this.rectSelect = rectSelect;
        painter.invalidateSelectedLines();
    }

    /**
     * Returns the position of the highlighted bracket (the bracket
     * matching the one before the caret)
     */
    public final int getBracketPosition() {
        return bracketPosition;
    }

    /**
     * Returns the line of the highlighted bracket (the bracket
     * matching the one before the caret)
     */
    public final int getBracketLine() {
        return bracketLine;
    }

    /**
     * Appends the given text to the end of the document.  Does nothing if
     * the model is null or the string is null or empty.
     * <p/>
     * This method is thread safe, although most Swing methods
     * are not. Please see
     * <A HREF="http://java.sun.com/products/jfc/swingdoc-archive/threads.html">Threads
     * and Swing</A> for more information.
     *
     * @param str the text to insert
     */
    public void append(String str) {
        Document doc = getSyntaxDocument();
        if (doc != null) {
            try {
                doc.insertString(doc.getLength(), str, null);
            }
            catch (BadLocationException e) {
            }
        }
    }

    void insertTabsInLines(int startLine, int endLine) {
        SyntaxDocument doc = getSyntaxDocument();
        if (doc != null) {
            doc.beginCompoundEdit();
            try {
                for (int i = startLine; i <= endLine; i++) {
                    int offset = getLineStartOffset(i);
                    try {
                        doc.insertString(offset, "\t", null);
                    }
                    catch (BadLocationException e) {
                    }
                }
            }
            finally {
                doc.endCompoundEdit();
            }
        }
    }
    /*
    void deleteTabsInLines(int startLine, int endLine) {
        SyntaxDocument doc = getSyntaxDocument();
        if (doc != null) {
            doc.beginCompoundEdit();
            try {
                for (int i = startLine; i <= endLine; i++) {
                    int offset = getLineStartOffset(i);
                    try {
                        doc.insertString(offset, "\t", null);
                    }
                    catch (BadLocationException e) {
                    }
                }
            }
            finally {
                doc.endCompoundEdit();
            }
        }
    }*/
    void deleteTabsInLines(int startLine, int endLine) {
        SyntaxDocument doc = getSyntaxDocument();
        if (doc != null) {
            doc.beginCompoundEdit();
            try {
                for (int i = startLine; i <= endLine; i++) {
                    int offset = getLineStartOffset(i);
                    String tempTxt = doc.getText(offset, getLineEndOffset(i) - offset);
                    if (tempTxt.length() > 1) {
                        char first = tempTxt.charAt(0);
                        if (first == '\t') {
                            doc.remove(offset, 1);
                        } else if (Character.isWhitespace(first)) {
                            int j = 0;
                            for (char c : tempTxt.toCharArray()) {
                                if (String.valueOf(c).matches("[^\\n\\t ]")) break;
                                if (String.valueOf(c).matches("[\\n ]")) ++j;
                            }
                            if (j > 3) doc.remove(offset, 4);
                            else doc.remove(offset, j);
                        }
                    }
                }
            } catch (BadLocationException e) {
            } finally {
                doc.endCompoundEdit();
            }
        }
    }

    public void replaceSelection(String content) {
        Document doc = getDocument();
        if (doc != null) {
            try {
                int p0 = getSelectionStart();//Math.min(caret.getDot(), caret.getMark());
                int p1 = getSelectionEnd(); //Math.max(caret.getDot(), caret.getMark());
                if (p0 != p1) {
                    doc.remove(p0, p1 - p0);
                }
                if (content != null && content.length() > 0) {
                    doc.insertString(p0, content, null);
                }
            }
            catch (BadLocationException e) {
                getToolkit().beep();
            }
        }
    }

    /**
     * Adds a caret change listener to this text area.
     *
     * @param listener The listener
     */
    public final void addCaretListener(CaretListener listener) {
        listenerList.add(CaretListener.class, listener);
    }

    /**
     * Removes a caret change listener from this text area.
     *
     * @param listener The listener
     */
    public final void removeCaretListener(CaretListener listener) {
        listenerList.remove(CaretListener.class, listener);
    }

    /**
     * Deletes the selected text from the text area and places it
     * into the clipboard.
     */
    public void cut() {
        if (editable && isEnabled()) {
            copy();
            setSelectedText("");
        }
    }

    /**
     * Places the selected text into the clipboard.
     */
    public void copy() {
        if (selectionStart != selectionEnd) {
            Clipboard clipboard = getToolkit().getSystemClipboard();

            String selection = getSelectedText();

            int repeatCount = inputHandler.getRepeatCount();
            StringBuffer buf = new StringBuffer();
            for (int i = 0; i < repeatCount; i++) {
                buf.append(selection);
            }
            String selectionHTML = null;
            if ((selectionEnd - selectionStart) < 100000) {
                selectionHTML = getAsHTML(selectionStart, selectionEnd);
                //System.out.println(selectionHTML);
            }
            TransferableImpl transferable = new TransferableImpl(selection, selectionHTML);
            clipboard.setContents(transferable, null);
            //clipboard.setContents(new StringSelection(buf.toString()), null);
        }
    }

    public void copyAsHTML() {
        if (selectionStart != selectionEnd) {
            Clipboard clipboard = getToolkit().getSystemClipboard();
            String selection = getSelectedText();
            String selectionHTML = getAsHTML(selectionStart, selectionEnd);
            selectionHTML = selectionHTML;
            TransferableImpl transferable = new TransferableImpl(selectionHTML, selectionHTML);
            //clipboard.setContents(new StringSelection(selection), null);
            clipboard.setContents(transferable, null);
        }
    }

    public String getAsHTML(int start, int end) {
        return getAsHTML(start, end, false);
    }

    public String getAsHTML(int start, int end, boolean fragment) {
        HTMLExport htmlExport = new HTMLExport(this, getTokenMarker());
        Location startLocation = getLocation(start);
        Location endLocation = getLocation(end);
        htmlExport.setFont(getPainter().getFont().getName(), getPainter().getFont().getSize());
        HTMLAppender htmlAppender = htmlExport.writeHTMLLines(startLocation.getLine() - 1, startLocation.getColumn() - 1, endLocation.getLine() - 1, endLocation.getColumn() - 1, fragment);
        String selection = htmlAppender.toString();
        return selection;
    }

    public String getAsHTML() {
        return getAsHTML(false);
    }

    public String getAsHTML(boolean fragment) {
        return getAsHTML(0, getDocumentLength(), fragment);
    }

    /**
     * Inserts the clipboard contents into the text.
     */
    public void paste() {
        if (canEdit()) {
            Clipboard clipboard = getToolkit().getSystemClipboard();
            try {
                // The MacOS MRJ doesn't convert \r to \n,
                // so do it here
                String selection = ((String) clipboard.getContents(this).getTransferData(DataFlavor.stringFlavor));

                selection = fixCRLF(selection);
                int repeatCount = inputHandler.getRepeatCount();
                StringBuffer buf = new StringBuffer();
                for (int i = 0; i < repeatCount; i++) {
                    buf.append(selection);
                }
                selection = buf.toString();
                setSelectedText(selection);
            }
            catch (Exception e) {
                getToolkit().beep();
                System.err.println("Clipboard does not"
                        + " contain a string");
            }
        }
    }

    private static String fixCRLF(String selection) {
        StringBuffer selbuf = new StringBuffer();
        for (int i = 0; i < selection.length(); ++i) {
            char c = selection.charAt(i);
            if (c == '\r') {
                if ((i + 1 >= selection.length() || selection.charAt(i + 1) != '\n')) {
                    c = '\n';
                }
            }
            selbuf.append(c);
        }
        return selbuf.toString();
    }

    public String getWordLeft(int offset) {
        return TextComponentUtils.getWordLeft(this, offset);
    }

    /**
     * Called by the AWT when this component is removed from it's parent.
     * This stops clears the currently focused component.
     */
    public void removeNotify() {
        super.removeNotify();
        if (getFocusedComponent() == this) {
            setFocusedComponent(null);
        }
    }

    /**
     * Forwards key events directly to the input handler.
     * This is slightly faster than using a KeyListener
     * because some Swing overhead is avoided.
     */
    //public void processKeyEvent(KeyEvent evt) {
    protected void processComponentKeyEvent(KeyEvent evt) {
        int keycode = evt.getKeyCode();
        int modifier = evt.getModifiers();
        if (inputHandler == null) {
            return;
        }
        switch (evt.getID()) {
            case KeyEvent.KEY_TYPED:
                inputHandler.keyTyped(evt);
                break;
            case KeyEvent.KEY_PRESSED:
                inputHandler.keyPressed(evt);
                break;
            case KeyEvent.KEY_RELEASED:
                inputHandler.keyReleased(evt);
                //CORE-2419 Adding alternative Key combination(shift+F10) for Context Menu in addition to Context Key
                if (keycode == SwingUtils.VK_CONTEXT_MENU || (modifier == KeyEvent.SHIFT_MASK && keycode == KeyEvent.VK_F10)) {
                    showPopup();
                }
                break;
        }
        if (evt.getID() == KeyEvent.KEY_PRESSED) {
            if (evt.isControlDown() && evt.isAltDown()) {
                int diff = keycode - KeyEvent.VK_0;
                if (diff >= 0 && diff < SwingUtils.ANTI_NAMES.length) {
                    SwingUtils.ANTI_NAME = SwingUtils.ANTI_NAMES[diff];
                    painter.repaint();
                }
            }
        }


    }

    // protected members
    protected static String CENTER = "center";
    protected static String RIGHT = "right";
    protected static String LEFT = "left";
    protected static String BOTTOM = "bottom";


    protected TextAreaPainter painter;

    //protected JPopupMenu popup;
    java.util.List actions = new ArrayList();
    static FindInfo findInfo = new FindInfo();
    protected EventListenerList listenerList;
    protected MutableCaretEvent caretEvent;

    protected boolean caretBlinks;
    protected boolean caretVisible;
    protected boolean blink;

    protected boolean editable;

    protected int firstLine;
    protected int visibleLines;
    protected int electricScroll;

    protected int horizontalOffset;

    protected JScrollBar vertical;
    protected JScrollBar horizontal;
    protected boolean scrollBarsInitialized;

    protected InputHandler inputHandler;
    protected SyntaxDocument document;
    protected DocumentHandler documentHandler;
    private UndoManager undoManager;

    protected Segment lineSegment;

    protected int selectionStart;
    protected int selectionStartLine;
    protected int selectionEnd;
    protected int selectionEndLine;
    protected boolean biasLeft;

    protected int bracketPosition;
    protected int bracketLine;

    protected int magicCaret;
    protected boolean overwrite;
    protected boolean rectSelect;

    protected void fireCaretEvent() {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i--) {
            if (listeners[i] == CaretListener.class) {
                ((CaretListener) listeners[i + 1]).caretUpdate(caretEvent);
            }
        }
    }

    protected void updateBracketHighlight(int newCaretPosition) {
        if (newCaretPosition == 0) {
            bracketPosition = bracketLine = -1;
            return;
        }

        try {
            int offset = TextUtilities.findMatchingBracket(document, newCaretPosition - 1);
            if (offset != -1) {
                bracketLine = getLineOfOffset(offset);
                bracketPosition = offset - getLineStartOffset(bracketLine);
                return;
            }
        }
        catch (BadLocationException bl) {
            bl.printStackTrace();
        }

        bracketLine = bracketPosition = -1;
    }

    protected void documentChanged(DocumentEvent evt) {
        DocumentEvent.ElementChange ch = evt.getChange(document.getDefaultRootElement());

        int count;
        if (ch == null) {
            count = 0;
        }
        else {
            count = ch.getChildrenAdded().length -
                    ch.getChildrenRemoved().length;
        }

        int line = getLineOfOffset(evt.getOffset());
        if (count == 0) {
            painter.invalidateLine(line);
        }
        // do magic stuff
        else if (line < firstLine) {
            setFirstLine(firstLine + count);
        }
        // end of magic stuff
        else {
            painter.invalidateLineRange(line, firstLine + visibleLines);
        }
        updateScrollBars();
    }

    public void showLineNumbers(boolean show) {
        if (show) {
            addLineDisplayPanel();
        }
        else {
            removeLineDisplayPanel();
        }
    }

    public boolean isLineNumbersVisible() {
        return lineGutter != null;
    }


    public void onGoTo() {
        int maxLine = getLineCount();
        String lineStr = JOptionPane.showInputDialog(this, "Line Number (1 - " + maxLine + "):", "Go To Line", JOptionPane.QUESTION_MESSAGE);
        if (StringUtils.isNotEmpty(lineStr)) {
            try {
                int line = Integer.parseInt(lineStr);
                int location = getLineStartOffset(line - 1);
                if (location != -1)
                    select(location, location);
            }
            catch (IllegalArgumentException e) {

            }
        }
    }

    public void onFindNext() {
        readGlobalFind();
        if (findInfo.getSearchText() == null) {
            onFind(findInfo);
        }
        else {
            find(findInfo);
        }
    }

    public void onFind() {
        readGlobalFind();
        onFind(findInfo);
    }

    private void readGlobalFind() {
        if (useGlobalFind) {
            final FindInfo globalFindInfo = FindInfo.getGlobalFindInfo();
            final List searchList = globalFindInfo.getSearchList();
            findInfo.setSearchList(searchList);
            if (searchList.size() > 0)
                findInfo.setSearchText((String) searchList.get(0));
            findInfo.setReplaceList(globalFindInfo.getReplaceList());
        }
    }

    private void writeGlobalFind() {
        if (useGlobalFind) {
            final FindInfo globalFindInfo = FindInfo.getGlobalFindInfo();
            globalFindInfo.setSearchList(findInfo.getSearchList());
            globalFindInfo.setReplaceList(findInfo.getReplaceList());
        }
    }

    public void onFind(FindInfo findInfo) {
        URL url = JEditTextArea.class.getResource("Find.xml");
        try (InputStream is = url.openStream()) {
            if (is != null) {
                Frame parentFrame = (Frame) SwingUtilities.getAncestorOfClass(Frame.class, this);
                GenericDialog searchDialog = new GenericDialog(parentFrame, "Find", is, findInfo);
                GenericPanel panel = searchDialog.getGenericPanel();
                panel.setComboItems("searchText", findInfo.getSearchList(), true);
                //cmp.selectAll();
                searchDialog.setLocationRelativeTo(searchDialog.getParent());
                if (searchDialog.showDialog()) {
                    findInfo = (FindInfo) searchDialog.getComponentObject();
                    FindInfo.addMRUList(findInfo.getSearchList(), findInfo.getSearchText());
                    writeGlobalFind();
                    find(findInfo);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onReplace() {
        if (isEditable()) {
            readGlobalFind();
            replace(findInfo);
        }
    }


    public void replace(FindInfo findInfo) {
        URL url = JEditTextArea.class.getResource("FindReplace.xml");
        try (InputStream is = url.openStream()) {
            int searchOriginalStart = getCaretPosition();
            if (is != null) {
                Frame parentFrame = (Frame) SwingUtilities.getAncestorOfClass(Frame.class, this);
                GenericDialog searchDialog = new GenericDialog(parentFrame, "Replace Text", is, findInfo);
                GenericPanel panel = searchDialog.getGenericPanel();

                panel.setComboItems("searchText", findInfo.getSearchList(), true);
                panel.setComboItems("replaceText", findInfo.getReplaceList(), true);

                //cmp.selectAll();
                if (searchDialog.showDialog()) {
                    findInfo = (FindInfo) searchDialog.getComponentObject();
                    FindInfo.addMRUList(findInfo.getSearchList(), findInfo.getSearchText());
                    FindInfo.addMRUList(findInfo.getReplaceList(), findInfo.getReplaceText());
                    writeGlobalFind();
                    if (find(findInfo, searchOriginalStart, true)) {
                        while (true) {
                            int ret = showReplaceOptionDialog();
                            if (ret == 0) { //replace
                                this.replaceSelection(findInfo.getReplaceText());
                            }
                            else if (ret == 1) { // skip
                            }
                            else if (ret == 2) { // replace all
                                while (true) {
                                    this.replaceSelection(findInfo.getReplaceText());
                                    if (!find(findInfo, searchOriginalStart, false)) {
                                        break;
                                    }
                                }
                                break;
                            }
                            else if (ret == 3) { //cancel
                                break;
                            }
                            else { //cancel
                                break;
                            }
                            if (!find(findInfo, searchOriginalStart, false)) {
                                break;
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }


    private int showReplaceOptionDialog() {
        class ReplaceOptionPanel extends DialogPanel {
            int retValue;

            ReplaceOptionPanel() {
                JPanel buttons = new JPanel();
                buttons.setLayout(new EqualsLayout(10));
                buttons.add(createActionButton("Replace", 1));
                buttons.add(createActionButton("Skip", 2));
                buttons.add(createActionButton("All", 3));
                buttons.add(createActionButton("Cancel", 0));
                setLayout(new BorderLayout());
                JPanel labelPanel = new JPanel();
                add(labelPanel, BorderLayout.NORTH);
                add(buttons, BorderLayout.SOUTH);
            }

            private JButton createActionButton(String name, final int action) {
                JButton replaceButton = new JButton(name);
                replaceButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        dispose(action);
                    }
                });
                return replaceButton;
            }

            private void dispose(int retValue) {
                this.retValue = retValue;

            }

            public boolean save() {
                return true;
            }

            public boolean checkValid() throws IllegalStateException {
                return true;
            }
        }
        /*
        ReplaceOptionPanel replaceOptionPanel = new ReplaceOptionPanel();
        JDialog dlg = new JDialog(getRootParent(JEditTextArea.this));
        dlg.getContentPane().setLayout(new BorderLayout());
        dlg.getContentPane().add(replaceOptionPanel);
        dlg.setTitle("Replace");
        dlg.setVisible(true);
        return replaceOptionPanel.retValue;*/
        String[] options = {"Replace", "Skip", "All", "Cancel"};
        int result = JOptionPane.showOptionDialog(
                this,                             // the parent that the dialog blocks
                "Do you want to replace this occurrence?", // the dialog message array
                "Replace", // the title of the dialog window
                JOptionPane.DEFAULT_OPTION,                 // option type
                JOptionPane.INFORMATION_MESSAGE,            // message type
                null,                                       // optional icon, use null to use the default icon
                options,                                    // options string array, will be made into buttons
                options[0]                                  // option that should be made into a default button
        );
        return result;
    }

    public static Frame getRootParent(Component cmp) {
        Component parent = cmp;
        while (parent != null && !(parent instanceof Frame)) {
            parent = parent.getParent();
        }
        return (Frame) parent;
    }

    public boolean find(FindInfo findInfo) {
        int searchOriginalStart = getCaretPosition();
        return find(findInfo, searchOriginalStart, true);
    }

    public boolean find(FindInfo findInfo, int searchOriginalStart, boolean warnNoMatch) {
        int start = getCaretPosition();
        int end = getDocument().getLength();
        String searchText = findInfo.getSearchText();
        boolean caseSensitive = findInfo.isMatchCase();
        boolean exact = findInfo.isMatchWord();

        try {
            if (searchOriginalStart <= start && searchComponent(searchText, start, end, caseSensitive, exact)) {
                return true;
            }
            if (searchComponent(searchText, 0, searchOriginalStart, caseSensitive, exact)) {
                return true;
            }
            if (warnNoMatch)
                JOptionPane.showMessageDialog(this, "No occurrence of '" + searchText + "' found", "No Matches", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        catch (BadLocationException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean searchComponent(String searchText, int start, int end, boolean matchCase, boolean exact) throws BadLocationException {
        String findIn = this.getText(start, end - start);
        int index = getMatch(findIn, searchText, matchCase, exact);
        if (index != -1) {
            int newSelectStart = start + index;
            int newSelectEnd = newSelectStart + searchText.length();
            if (this.getSelectionStart() != newSelectStart || this.getSelectionEnd() != newSelectEnd) {
                this.select(newSelectStart, newSelectEnd);
                this.requestFocus();
                return true;
            }
        }
        return false;
    }

    private static int getMatch(String findIn, String searchText, boolean matchCase, boolean exact) {
        if (!matchCase) {
            searchText = searchText.toLowerCase();
            findIn = findIn.toLowerCase();
        }
        int index = findIn.indexOf(searchText);
        if (exact) {
            int endIndex = index + searchText.length();
            if (index != -1 && (endIndex == findIn.length() || !Character.isJavaIdentifierPart(findIn.charAt(endIndex)))) {
                if (index == 0 || !Character.isJavaIdentifierPart(findIn.charAt(index - 1))) {
                    return index;
                }

            }
            return -1;
        }
        else {
            return index;
        }
    }

    public Dimension getPreferredScrollableViewportSize0() {
        return getPreferredSize();
    }

    public Dimension getPreferredScrollableViewportSize() {
        Dimension size = getPreferredScrollableViewportSize0();
        size = (size == null) ? new Dimension(400, 400) : size;
        int maxLengthLine = getMaxLengthLine();
        String text = getLineText(maxLengthLine);
        int columns = 2000;
        size.width = (columns == 0) ? size.width : columns * getColumnWidth();
        size.height = (getLineCount() == 0) ? size.height : getLineCount() * getRowHeight();
        return size;
    }

    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        switch (orientation) {
            case SwingConstants.VERTICAL:
                return getRowHeight();
            case SwingConstants.HORIZONTAL:
                return getColumnWidth();
            default:
                throw new IllegalArgumentException("Invalid orientation: " + orientation);
        }
    }

    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        switch (orientation) {
            case SwingConstants.VERTICAL:
                return visibleRect.height;
            case SwingConstants.HORIZONTAL:
                return visibleRect.width;
            default:
                throw new IllegalArgumentException("Invalid orientation: " + orientation);
        }
    }

    public boolean getScrollableTracksViewportWidth() {
//        if (getParent() instanceof JViewport) {
//            return (((JViewport)getParent()).getWidth() > getPreferredSize().width);
//        }
//        return false;
        return false;
    }

    public boolean getScrollableTracksViewportHeight() {
        //if (getParent() instanceof JViewport) {
        //    return (((JViewport)getParent()).getHeight() > getPreferredSize().height);
        //}
        //return false;
        return false;
    }

    public String getLineSeparator() {
        return lineSeparator;
    }

    public void setLineSeparator(String lineSeparator) {
        this.lineSeparator = lineSeparator;
    }

    void showPopup() {
        int position = getCaretPosition();
        int line = getLineOfOffset(position);
        int column = position - getLineStartOffset(line);
        int x = offsetToX(line, column);
        int y = lineToY(line);
        MouseEvent mouseEvent = new MouseEvent(painter, 1, 0, 0, x, y + getLineHeight(), 1, true);
        showPopup(mouseEvent);
    }

    void showPopup(MouseEvent evt) {
        onContextMenu(painter, evt.getPoint());
        enableDisableActions();
        if (actions.size() > 0) {
            JPopupMenu popup = ExtendedAction.createPopupMenu(actions);
            SwingUtils.cleanupMenu(popup);
            popup.show(painter, evt.getX(), evt.getY());
        }
        evt.consume();
    }

    protected void onContextMenu(JComponent painter, Point point) {
        onContextMenu();
    }

    protected void onContextMenu() {

    }

    void enableDisableActions() {
        cutAction.setEnabled(canEdit());
        pasteAction.setEnabled(canEdit());
        replaceAction.setEnabled(canEdit());
        showLineNumbersAction.setName(isLineNumbersVisible() ? "Hide Line Numbers" : "Show Line Numbers");
    }

    protected boolean canEdit() {
        return isEditable() && isEnabled();
    }

    public void postActionEvent() {
        fireActionPerformed();
    }

    class ScrollLayout implements LayoutManager {
        public void addLayoutComponent(String name, Component comp) {
            if (name.equals(CENTER)) {
                center = comp;
            }
            else if (name.equals(RIGHT)) {
                right = comp;
            }
            else if (name.equals(LEFT)) {
                left = comp;
            }
            else if (name.equals(BOTTOM)) {
                bottom = comp;
            }
            else if (name.equals(LEFT_OF_SCROLLBAR)) {
                leftOfScrollBar.addElement(comp);
            }
            else if (name.equals(LEFT_OF_PANE)) {
                leftOfPane.addElement(comp);
            }
            else if (name.equals(RIGHT_OF_PANE)) {
                rightOfPane.addElement(comp);
            }
        }

        public void removeLayoutComponent(Component comp) {
            if (center == comp) {
                center = null;
            }
            if (right == comp) {
                right = null;
            }
            if (bottom == comp) {
                bottom = null;
            }
            else {
                leftOfScrollBar.removeElement(comp);
                leftOfPane.removeElement(comp);
                rightOfPane.removeElement(comp);
            }
        }

        public Dimension preferredLayoutSize(Container parent) {
            Dimension dim = new Dimension();
            Insets insets = parent.getInsets();
            dim.width = insets.left + insets.right;
            dim.height = insets.top + insets.bottom;
            {
                // Lay out all status components, in order
                Enumeration lstatus = leftOfPane.elements();
                while (lstatus.hasMoreElements()) {
                    Component comp = (Component) lstatus.nextElement();
                    Dimension losdim = comp.getPreferredSize();
                    dim.width += losdim.width;
                }
            }

            Dimension centerPref = center.getPreferredSize();
            dim.width += centerPref.width;
            dim.height += centerPref.height;
            Dimension rightPref = right == null ? new Dimension(0, 0) : right.getPreferredSize();
            Dimension leftPref = left == null ? new Dimension(0, 0) : left.getMinimumSize();
            dim.width += leftPref.width + rightPref.width;
            Dimension bottomPref = bottom.getPreferredSize();
            dim.height += bottomPref.height;

            {
                // Lay out all status components, in order
                Enumeration lstatus = rightOfPane.elements();
                while (lstatus.hasMoreElements()) {
                    Component comp = (Component) lstatus.nextElement();
                    Dimension losdim = comp.getPreferredSize();
                    dim.width += losdim.width;
                }
            }

            return dim;
        }

        public Dimension minimumLayoutSize(Container parent) {
            Dimension dim = new Dimension();
            Insets insets = parent.getInsets();
            dim.width = insets.left + insets.right;
            dim.height = insets.top + insets.bottom;
            {
                // Lay out all status components, in order
                Enumeration lstatus = leftOfPane.elements();
                while (lstatus.hasMoreElements()) {
                    Component comp = (Component) lstatus.nextElement();
                    Dimension losdim = comp.getMinimumSize();
                    dim.width += losdim.width;
                }
            }

            Dimension centerPref = center.getMinimumSize();
            dim.width += centerPref.width;
            dim.height += centerPref.height;
            Dimension rightPref = right == null ? new Dimension(0, 0) : right.getMinimumSize();
            Dimension leftPref = left == null ? new Dimension(0, 0) : left.getMinimumSize();
            dim.width += leftPref.width + rightPref.width;
            Dimension bottomPref = bottom.getMinimumSize();
            dim.height += bottomPref.height;

            return dim;
        }

        public void layoutContainer(Container parent) {
            Dimension size = parent.getSize();
            Insets insets = parent.getInsets();
            int itop = insets.top;
            int ileft = insets.left;
            int ibottom = insets.bottom;
            int iright = insets.right;

            int rightWidth = right == null ? 0 : right.getPreferredSize().width;
            int leftWidth = left == null ? 0 : left.getPreferredSize().width;
            int bottomHeight = bottom.getPreferredSize().height;
            int centerHeight = size.height - bottomHeight - itop - ibottom;
            {
                // Lay out all status components, in order
                Enumeration lstatus = leftOfPane.elements();
                while (lstatus.hasMoreElements()) {
                    Component comp = (Component) lstatus.nextElement();
                    Dimension dim = comp.getPreferredSize();
                    comp.setBounds(ileft, itop, dim.width, size.height - itop - ibottom);
                    ileft += dim.width;
                }
            }
            {
                // Lay out all status components, in order
                Enumeration lstatus = rightOfPane.elements();
                while (lstatus.hasMoreElements()) {
                    Component comp = (Component) lstatus.nextElement();
                    Dimension dim = comp.getPreferredSize();
                    comp.setBounds(size.width - iright - dim.width, itop, dim.width, size.height - itop - ibottom);
                    iright += dim.width;
                }
            }
            int centerWidth = size.width - rightWidth - leftWidth - ileft - iright;
            //ileft += 4;
            if (left != null)
                left.setBounds(ileft, itop, leftWidth, centerHeight);

            center.setBounds(ileft + leftWidth, itop, centerWidth, centerHeight);
            if (right != null)
                right.setBounds(ileft + leftWidth + centerWidth, itop, rightWidth, centerHeight);
            // Lay out all status components, in order
            Enumeration status = leftOfScrollBar.elements();
            while (status.hasMoreElements()) {
                Component comp = (Component) status.nextElement();
                Dimension dim = comp.getPreferredSize();
                comp.setBounds(ileft + leftWidth, itop + centerHeight, dim.width, bottomHeight);
                ileft += dim.width;
            }


            bottom.setBounds(ileft + leftWidth, itop + centerHeight, size.width - rightWidth - leftWidth - ileft - iright, bottomHeight);
        }

        // private members
        private Component center;
        private Component right;
        private Component left;
        private Component bottom;
        private Vector leftOfScrollBar = new Vector();
        private Vector leftOfPane = new Vector();
        private Vector rightOfPane = new Vector();
    }


    class MutableCaretEvent extends CaretEvent {
        MutableCaretEvent() {
            super(JEditTextArea.this);
        }

        public int getDot() {
            return getCaretPosition();
        }

        public int getMark() {
            return getMarkPosition();
        }
    }

    class AdjustHandler implements AdjustmentListener {
        public void adjustmentValueChanged(final AdjustmentEvent evt) {
            if (!scrollBarsInitialized) {
                return;
            }

            // If this is not done, mousePressed events accumilate
            // and the result is that scrolling doesn't stop after
            // the mouse is released
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    if (evt.getAdjustable() == vertical) {
                        setFirstLine(vertical.getValue());
                    }
                    else {
                        setHorizontalOffset(-horizontal.getValue());
                    }
                }
            });
        }
    }

    class ComponentHandler extends ComponentAdapter {
        public void componentResized(ComponentEvent evt) {
            recalculateVisibleLines();
            scrollBarsInitialized = true;
        }
    }

    class DocumentHandler implements DocumentListener {
        public void insertUpdate(DocumentEvent evt) {
            documentChanged(evt);

            int offset = evt.getOffset();
            int length = evt.getLength();

            int newStart;
            int newEnd;

            if (selectionStart > offset || (selectionStart
                    == selectionEnd && selectionStart == offset)) {
                newStart = selectionStart + length;
            }
            else {
                newStart = selectionStart;
            }

            if (selectionEnd >= offset) {
                newEnd = selectionEnd + length;
            }
            else {
                newEnd = selectionEnd;
            }

            select(newStart, newEnd);
        }

        public void removeUpdate(DocumentEvent evt) {
            documentChanged(evt);

            int offset = evt.getOffset();
            int length = evt.getLength();

            int newStart;
            int newEnd;

            if (selectionStart > offset) {
                if (selectionStart > offset + length) {
                    newStart = selectionStart - length;
                }
                else {
                    newStart = offset;
                }
            }
            else {
                newStart = selectionStart;
            }

            if (selectionEnd > offset) {
                if (selectionEnd > offset + length) {
                    newEnd = selectionEnd - length;
                }
                else {
                    newEnd = offset;
                }
            }
            else {
                newEnd = selectionEnd;
            }

            select(newStart, newEnd);
        }

        public void changedUpdate(DocumentEvent evt) {
        }
    }

    class FocusHandler implements FocusListener {
        public void focusGained(FocusEvent evt) {
            setCaretVisible(true);
            setFocusedComponent(JEditTextArea.this);
        }

        public void focusLost(FocusEvent evt) {
            setCaretVisible(false);
            setFocusedComponent(null);
            spokenLine = -1;
        }
    }

    class MouseHandler extends MouseAdapter {
        public void mousePressed(MouseEvent evt) {
            requestFocus();

            // Focus events not fired sometimes?
            setCaretVisible(true);
            setFocusedComponent(JEditTextArea.this);

            if ((SwingUtils.isPopupTrigger(evt))) {
                showPopup(evt);
                return;
            }

            int line = yToLine(evt.getY());
            int offset = xToOffset(line, evt.getX());
            int dot = getLineStartOffset(line) + offset;

            switch (evt.getClickCount()) {
                case 1:
                    doSingleClick(evt, line, offset, dot);
                    break;
                case 2:
                    // It uses the bracket matching stuff, so
                    // it can throw a BLE
                    try {
                        doDoubleClick(evt, line, offset, dot);
                    } catch (Exception bl) {
                        bl.printStackTrace();
                    }
                    break;
                case 3:
                    doTripleClick(evt, line, offset, dot);
                    break;
            }
        }

        //CORE-4456 Moved to single mouse adaptor to control all events in a single object
        public void mouseDragged(MouseEvent evt) {
            /*if (popup != null && popup.isVisible())
                return;*/
            //if (mouseSelectionMode)
            {
                dragActive = true;
                setSelectionRectangular((evt.getModifiers()
                        & ctrlMask) != 0);
                select(getMarkPosition(), xyToOffset(evt.getX(), evt.getY()));
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            //CORE-4456 Update the accessibility text when mouse dragged is finished
            if (dragActive) {
                dragActive = false;
                onCaretUpdate();
            }
        }

        private void doSingleClick(MouseEvent evt, int line,
                                   int offset, int dot) {
            int modifiers = evt.getModifiers();
            if ((modifiers & InputEvent.SHIFT_MASK) != 0) {
                rectSelect = (modifiers & ctrlMask) != 0;
                select(getMarkPosition(), dot);
            }
            else {
                int selectionStart = getSelectionStart();
                int selectionEnd = getSelectionEnd();
                setCaretPosition(dot);
                /*
                if (dot >= selectionStart && dot < selectionEnd) {
                    mouseSelectionMode = false;
                }
                else {
                }*/
            }
        }

        private void doDoubleClick(MouseEvent evt, int line,
                                   int offset, int dot) throws BadLocationException {
            // Ignore empty lines
            if (offset < 0 || line < 0) {
                return;
            }
            int lineLength = getLineLength(line);
            if (lineLength == 0 || offset >= lineLength) {
                return;
            }

            try {
                int bracket = TextUtilities.findMatchingBracket(document, Math.max(0, dot - 1));
                if (bracket != -1) {
                    int mark = getMarkPosition();
                    // Hack
                    if (bracket > mark) {
                        bracket++;
                        mark--;
                    }
                    if (mark >= 0) {
                        select(mark, bracket);
                    }
                    return;
                }
            }
            catch (IllegalArgumentException | BadLocationException bl) {
                bl.printStackTrace();
            }

            // Ok, it's not a bracket... select the word
            String lineText = getLineText(line);
            char ch = lineText.charAt(Math.max(0, offset - 1));

            String noWordSep = (String) document.getProperty("noWordSep");
            if (noWordSep == null) {
                noWordSep = "";
            }

            // If the user clicked on a non-letter char,
            // we select the surrounding non-letters
            boolean selectNoLetter = (!Character
                    .isLetterOrDigit(ch)
                    && noWordSep.indexOf(ch) == -1);

            int wordStart = 0;

            for (int i = offset - 1; i >= 0; i--) {
                ch = lineText.charAt(i);
                if (selectNoLetter ^ (!Character
                        .isLetterOrDigit(ch) &&
                        noWordSep.indexOf(ch) == -1)) {
                    wordStart = i + 1;
                    break;
                }
            }

            int wordEnd = lineText.length();
            for (int i = offset; i < lineText.length(); i++) {
                ch = lineText.charAt(i);
                if (selectNoLetter ^ (!Character
                        .isLetterOrDigit(ch) &&
                        noWordSep.indexOf(ch) == -1)) {
                    wordEnd = i;
                    break;
                }
            }

            int lineStart = getLineStartOffset(line);
            select(lineStart + wordStart, lineStart + wordEnd);

            /*
            String lineText = getLineText(line);
            String noWordSep = (String)document.getProperty("noWordSep");
            int wordStart = TextUtilities.findWordStart(lineText,offset,noWordSep);
            int wordEnd = TextUtilities.findWordEnd(lineText,offset,noWordSep);

            int lineStart = getLineStartOffset(line);
            select(lineStart + wordStart,lineStart + wordEnd);
            */
        }

        private void doTripleClick(MouseEvent evt, int line,
                                   int offset, int dot) {
            select(getLineStartOffset(line), getLineEndOffset(line) - 1);
        }
    }

    class CaretUndo extends AbstractUndoableEdit {
        private int start;
        private int end;

        CaretUndo(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public boolean isSignificant() {
            return false;
        }

        public String getPresentationName() {
            return "caret move";
        }

        public void undo() throws CannotUndoException {
            super.undo();

            select(start, end);
        }

        public void redo() throws CannotRedoException {
            super.redo();

            select(start, end);
        }

        public boolean addEdit(UndoableEdit edit) {
            if (edit instanceof CaretUndo) {
                CaretUndo cedit = (CaretUndo) edit;
                start = cedit.start;
                end = cedit.end;
                cedit.die();

                return true;
            }
            else {
                return false;
            }
        }
    }

    static class CaretBlinker implements ActionListener {
        public void actionPerformed(ActionEvent evt) {
            JEditTextArea focusedComponent = getFocusedComponent();
            if (focusedComponent != null
                    && focusedComponent.hasFocus()) {
                focusedComponent.blinkCaret();
            }
        }
    }

    static JEditTextArea getFocusedComponent() {
        if (focusedComponentRef != null) {
            return (JEditTextArea) focusedComponentRef.get();
        }
        return null;
    }

    static void setFocusedComponent(JEditTextArea editTextArea) {
        if (editTextArea != null) {
            focusedComponentRef = new WeakReference(editTextArea);
        }
        else {
            focusedComponentRef = null;
        }
    }

    private static final String DEFAULT_CODE_FONT = "Monospaced";

    public synchronized static boolean isAntiAlias() {
        Boolean anti = (Boolean) UIManager.get("code.antialias");
        return anti == null ? true : anti;
    }

    public synchronized static Font getDefaultCodeFont() {
        Font font = UIManager.getFont("code.font");
        if (font == null) {
            int size = SwingUtils.getTextFontSize() + 2;
            font = new Font(DEFAULT_CODE_FONT, Font.PLAIN, size);
        }
        return font;
    }

    public synchronized static String getLinkedFontNames() {
        String fontNames = UIManager.getString("linked.fontnames");
        if (fontNames == null) {
            fontNames = "Dialog";
        }
        return fontNames;

    }

    Map highlightTypeVsHighlighter = new HashMap();
    MultiLocationHighlight locationHighlight = new MultiLocationHighlight(highlightTypeVsHighlighter);
    public static final Object ERROR_TYPE = new Object();

    {
        addHighlighter(ERROR_TYPE, new ErrorHighlighter());
    }

    public void addHighlighter(Object type, Highlighter highlighter) {
        highlightTypeVsHighlighter.put(type, highlighter);
    }

    public void addErrorHighlight(String message, Location startLocation, Location endLocation) {
        int errStart = getOffset(startLocation);
        int errEnd = getOffset(endLocation);
        TextRefInfo textRefInfo = new ErrorTextRefInfo("", message);
        HighlightInfo highlightInfo = new HighlightInfo(errStart, errEnd, textRefInfo, ERROR_TYPE);
        addHighlight(highlightInfo);
    }

    public void addHighlight(HighlightInfo highlightInfo) {
        locationHighlight.addHighlight(highlightInfo);
    }

    public void removeHighlight(HighlightInfo highlightInfo) {
        locationHighlight.removeHighlight(highlightInfo);
    }

    public void clearHighlight(Object type) {
        locationHighlight.clear(type);
    }

    public void clearErrors() {
        locationHighlight.clear(ERROR_TYPE);
    }


    protected static WeakReference focusedComponentRef;
    protected static Timer caretTimer;

    static {
        caretTimer = new Timer(500, new CaretBlinker());
        caretTimer.setInitialDelay(500);
        caretTimer.start();
    }

    //CORE-4456 Provided accessibility support for JEditTextArea
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.TEXT;
                }
            };
        }
        return accessibleContext;
    }
}

