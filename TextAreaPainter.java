/*
*  TextAreaPainter.java
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
 * TextAreaPainter.java - Paints the text area
 * Copyright (C) 1999 Slava Pestov
 *
 * You may use and modify this package for any purpose. Redistribution is
 * permitted, in both source and binary form, provided that this notice
 * remains intact in all source distributions of this package.
 */

import com.tplus.transform.swing.CustomToolTip;
import com.tplus.transform.swing.SwingUtils;
import com.tplus.transform.swing.text.marker.TokenMarker;
import com.tplus.transform.swing.text.token.Token;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import javax.swing.text.PlainDocument;
import javax.swing.text.Segment;
import javax.swing.text.TabExpander;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * The text area repaint manager. It performs double buffering and paints
 * lines of text.
 *
 * @author Slava Pestov
 * @version $Id: TextAreaPainter.java,v 1.24 1999/12/13 03:40:30 sp Exp $
 */
public class TextAreaPainter extends JComponent implements TabExpander, Accessible {
    private Color specialCharColor;
    Insets margin = new Insets(0, 0, 0, 0);
    LogicalFont logicalFont;
    boolean syntaxHighlight = true; //CORE-3089


    /**
     * Creates a new repaint manager. This should be not be called
     * directly.
     */
    public TextAreaPainter(JEditTextArea textArea, TextAreaDefaults defaults) {
        this.textArea = textArea;

        setAutoscrolls(true);
        setDoubleBuffered(true);
        setOpaque(true);

        ToolTipManager.sharedInstance().registerComponent(this);

        currentLine = new Segment();
        currentLineIndex = -1;

        setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        Font codeFont = JEditTextArea.getDefaultCodeFont();
        setFont(codeFont);
        //CORE-1270 HIGH CONTRAST SUPPORT
        // get colors from UIManager
        setForeground(UIManager.getColor("Text.foreground"));
        setBackground(UIManager.getColor("Window.background"));

        blockCaret = defaults.blockCaret;
        styles = SyntaxUtilities.getDefaultSyntaxStyles();
        cols = defaults.cols;
        rows = defaults.rows;
        caretColor = defaults.caretColor;
        selectionBackgroundColor = defaults.selectionBackgroundColor;
        selectionForegroundColor = defaults.selectionForegroundColor;
        lineHighlightColor = defaults.lineHighlightColor;
        lineHighlight = defaults.lineHighlight;
        bracketHighlightColor = defaults.bracketHighlightColor;
        bracketHighlight = defaults.bracketHighlight;
        paintInvalid = defaults.paintInvalid;
        eolMarkerColor = defaults.eolMarkerColor;
        //CORE-2676 Getting color from UIManager
        specialCharColor = UIManager.getColor("Text.inactiveForeground");
        eolMarkers = defaults.eolMarkers;
        showCRLF = defaults.showCRLF;
    }

    /**
     * Returns if this component can be traversed by pressing the
     * Tab key. This returns false.
     */
    public final boolean isManagingFocus() {
        return false;
    }

    /**
     * Returns the syntax styles used to paint colorized text. Entry <i>n</i>
     * will be used to paint tokens with id = <i>n</i>.
     *
     * @see Token
     */
    public final SyntaxStyle[] getStyles() {
        return styles;
    }

    /**
     * Sets the syntax styles used to paint colorized text. Entry <i>n</i>
     * will be used to paint tokens with id = <i>n</i>.
     *
     * @param styles The syntax styles
     * @see Token
     */
    public final void setStyles(SyntaxStyle[] styles) {
        this.styles = styles;
        repaint();
    }

    /**
     * Returns the caret color.
     */
    public final Color getCaretColor() {
        return caretColor;
    }

    /**
     * Sets the caret color.
     *
     * @param caretColor The caret color
     */
    public final void setCaretColor(Color caretColor) {
        this.caretColor = caretColor;
        invalidateSelectedLines();
    }

    /**
     * Returns the selection color.
     */
    public final Color getSelectionBackgroundColor() {
        return selectionBackgroundColor;
    }

    /**
     * Sets the selection color.
     *
     * @param selectionBackgroundColor The selection color
     */
    public final void setSelectionBackgroundColor(Color selectionBackgroundColor) {
        this.selectionBackgroundColor = selectionBackgroundColor;
        invalidateSelectedLines();
    }

    public Color getSelectionForegroundColor() {
        return selectionForegroundColor;
    }

    public void setSelectionForegroundColor(Color selectionForegroundColor) {
        this.selectionForegroundColor = selectionForegroundColor;
    }

    /**
     * Returns the line highlight color.
     */
    public final Color getLineHighlightColor() {
        return lineHighlightColor;
    }

    /**
     * Sets the line highlight color.
     *
     * @param lineHighlightColor The line highlight color
     */
    public final void setLineHighlightColor(Color lineHighlightColor) {
        this.lineHighlightColor = lineHighlightColor;
        invalidateSelectedLines();
    }

    /**
     * Returns true if line highlight is enabled, false otherwise.
     */
    public final boolean isLineHighlightEnabled() {
        return lineHighlight;
    }

    /**
     * Enables or disables current line highlighting.
     *
     * @param lineHighlight True if current line highlight should be enabled,
     *                      false otherwise
     */
    public final void setLineHighlightEnabled(boolean lineHighlight) {
        this.lineHighlight = lineHighlight;
        invalidateSelectedLines();
    }

    /**
     * Returns the bracket highlight color.
     */
    public final Color getBracketHighlightColor() {
        return bracketHighlightColor;
    }

    /**
     * Sets the bracket highlight color.
     *
     * @param bracketHighlightColor The bracket highlight color
     */
    public final void setBracketHighlightColor(Color bracketHighlightColor) {
        this.bracketHighlightColor = bracketHighlightColor;
        invalidateLine(textArea.getBracketLine());
    }

    /**
     * Returns true if bracket highlighting is enabled, false otherwise.
     * When bracket highlighting is enabled, the bracket matching the
     * one before the caret (if any) is highlighted.
     */
    public final boolean isBracketHighlightEnabled() {
        return bracketHighlight;
    }

    /**
     * Enables or disables bracket highlighting.
     * When bracket highlighting is enabled, the bracket matching the
     * one before the caret (if any) is highlighted.
     *
     * @param bracketHighlight True if bracket highlighting should be
     *                         enabled, false otherwise
     */
    public final void setBracketHighlightEnabled(boolean bracketHighlight) {
        this.bracketHighlight = bracketHighlight;
        invalidateLine(textArea.getBracketLine());
    }

    /**
     * Returns true if the caret should be drawn as a block, false otherwise.
     */
    public final boolean isBlockCaretEnabled() {
        return blockCaret;
    }

    /**
     * Sets if the caret should be drawn as a block, false otherwise.
     *
     * @param blockCaret True if the caret should be drawn as a block,
     *                   false otherwise.
     */
    public final void setBlockCaretEnabled(boolean blockCaret) {
        this.blockCaret = blockCaret;
        invalidateSelectedLines();
    }

    /**
     * Returns the EOL marker color.
     */
    public final Color getEOLMarkerColor() {
        return eolMarkerColor;
    }

    /**
     * Sets the EOL marker color.
     *
     * @param eolMarkerColor The EOL marker color
     */
    public final void setEOLMarkerColor(Color eolMarkerColor) {
        this.eolMarkerColor = eolMarkerColor;
        repaint();
    }

    /**
     * Returns true if EOL markers are drawn, false otherwise.
     */
    public final boolean getEOLMarkersPainted() {
        return eolMarkers;
    }

    /**
     * Sets if EOL markers are to be drawn.
     *
     * @param eolMarkers True if EOL markers should be drawn, false otherwise
     */
    public final void setEOLMarkersPainted(boolean eolMarkers) {
        this.eolMarkers = eolMarkers;
        repaint();
    }

    //CORE-3089 Syntax Highlight flag setter method
    /**
     * Sets whether Syntax to be highlighted or not
     *
     * @param syntaxHighlight True to highlight, false otherwise
     */
    public void setSyntaxHighlightEnabled(boolean syntaxHighlight) {
        this.syntaxHighlight = syntaxHighlight;
    }

    public Insets getMargin() {
        return margin;
    }

    public void setMargin(Insets margin) {
        this.margin = margin;
    }

    public Insets getInsets() {
        return margin;
    }

    public void repaint() {
        super.repaint();
    }

    public int getWidth() {
        return super.getWidth();
    }

    public int getHeight() {
        return super.getHeight();
    }

    public void repaint(int i, int i1, int width, int lineHeight) {
        super.repaint(i, i1, width, lineHeight);
    }

    public Color getBackground() {
        return super.getBackground();
    }

    public Color getForeground() {
        return super.getForeground();
    }

    public Font getFont() {
        return super.getFont();
    }

    public LogicalFont getLogicalFont() {
        return logicalFont;
    }

    /**
     * Returns true if invalid lines are painted as red tildes (~),
     * false otherwise.
     */
    public boolean getInvalidLinesPainted() {
        return paintInvalid;
    }

    /**
     * Sets if invalid lines are to be painted as red tildes.
     *
     * @param paintInvalid True if invalid lines should be drawn, false otherwise
     */
    public void setInvalidLinesPainted(boolean paintInvalid) {
        this.paintInvalid = paintInvalid;
    }

    /**
     * Adds a custom highlight painter.
     *
     * @param highlight The highlight
     */
    public void addCustomHighlight(Highlight highlight) {
        highlight.init(textArea, highlights);
        highlights = highlight;
    }

    public int getInterLineSpacing() {
        return interLineSpacing;
    }

    public void setInterLineSpacing(int interLineSpacing) {
        this.interLineSpacing = interLineSpacing;
        updateMetrics();
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return cols;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }

    public void setColumns(int cols) {
        this.cols = cols;
    }


    /**
     * Highlight interface.
     */
    public interface Highlight {
        /**
         * Called after the highlight painter has been added.
         *
         * @param textArea The text area
         * @param next     The painter this one should delegate to
         */
        void init(JEditTextArea textArea, Highlight next);

        /**
         * This should paint the highlight and delgate to the
         * next highlight painter.
         *
         * @param gfx  The graphics context
         * @param line The line number
         * @param y    The y co-ordinate of the line
         */
        void paintHighlight(Graphics gfx, int line, int y);

        /**
         * Returns the tool tip to display at the specified
         * location. If this highlighter doesn't know what to
         * display, it should delegate to the next highlight
         * painter.
         *
         * @param evt The mouse event
         */
        String getToolTipText(MouseEvent evt);
    }

    /**
     * Returns the tool tip to display at the specified location.
     *
     * @param evt The mouse event
     */
    public String getToolTipText(MouseEvent evt) {
        String tooltip = null;
        if (highlights != null) {
            tooltip = highlights.getToolTipText(evt);
        }
        return tooltip;
    }

    public JToolTip createToolTip() {
        JToolTip tip = new CustomToolTip();
        tip.setComponent(this);
        return tip;
    }


    /**
     * Returns the font metrics used by this component.
     */
    public FontMetrics2 getFontMetrics() {
        return fm;
    }

    /**
     * Sets the font for this component. This is overridden to update the
     * cached font metrics and to recalculate which lines are visible.
     *
     * @param font The font
     */
    public void setFont(Font font) {
        super.setFont(font);
        String primaryFontName = font.getName();
        String logicalNames = primaryFontName + "," + JEditTextArea.getLinkedFontNames();
        logicalFont = new LogicalFont(logicalNames, font.getStyle(), font.getSize());
        updateMetrics();
    }

    private void updateMetrics() {
        fm = new FontMetrics2(Toolkit.getDefaultToolkit().getFontMetrics(getFont()), interLineSpacing);
        textArea.recalculateVisibleLines();
    }

    public Color getPaintingBackground() {
        Color backcolor = null;
        if (textArea.canEdit()) {
            backcolor = getBackground();
        }
        else {
            if (textArea.getRows() == 1) {
                backcolor = UIManager.getColor("TextField.inactiveBackground");
            }
            else {
                backcolor = UIManager.getColor("TextArea.inactiveBackground");
            }
            if (backcolor == null) {
                backcolor = Color.white; //new Color(232, 232, 232);
            }
        }
        return backcolor;
    }

    /**
     * Repaints the text.
     *
     * @param gfx The graphics context
     */
    public void paint(Graphics gfx) {
        Graphics2D g2 = (Graphics2D) gfx;
        if (JEditTextArea.isAntiAlias()) {
            //g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, SwingUtils.getTextAntiAlias());
            //g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            //g2.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
            //g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            //g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            //g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }

        tabSize = fm.charWidth(' ') * ((Integer) textArea
                .getSyntaxDocument().getProperty(PlainDocument.tabSizeAttribute)).intValue();

        Rectangle clipRect = gfx.getClipBounds();
        gfx.setColor(getPaintingBackground());
        gfx.fillRect(clipRect.x, clipRect.y, clipRect.width, clipRect.height);

        // We don't use yToLine() here because that method doesn't
        // return lines past the end of the document
        int height = fm.getFontHeight();
        int firstLine = textArea.getFirstLine();
        int firstInvalid = firstLine + clipRect.y / height;
        // Because the clipRect's height is usually an even multiple
        // of the font height, we subtract 1 from it, otherwise one
        // too many lines will always be painted.
        int lastInvalid = firstLine + (clipRect.y + clipRect.height - 1) / height;

        try {
            TokenMarker tokenMarker = textArea.getSyntaxDocument()
                    .getTokenMarker();
            int x = textArea.getHorizontalOffset();

            for (int line = firstInvalid; line <= lastInvalid; line++) {
                paintLine(gfx, tokenMarker, line, x);
            }

            if (tokenMarker != null && tokenMarker.isNextLineRequested()) {
                int h = clipRect.y + clipRect.height;
                repaint(0, h, getWidth(), getHeight() - h);
            }
        }
        catch (Exception e) {
            System.err.println("Error repainting line"
                    + " range {" + firstInvalid + ","
                    + lastInvalid + "}:");
            e.printStackTrace();
        }
    }


    /**
     * Marks a line as needing a repaint.
     *
     * @param line The line to invalidate
     */
    public final void invalidateLine(int line) {
        repaint(0, textArea.lineToY(line) + fm.getMaxDescent() + fm.getLeading(),
                getWidth(), fm.getFontHeight());
    }

    /**
     * Marks a range of lines as needing a repaint.
     *
     * @param firstLine The first line to invalidate
     * @param lastLine  The last line to invalidate
     */
    public final void invalidateLineRange(int firstLine, int lastLine) {
        repaint(0, textArea.lineToY(firstLine) + fm.getMaxDescent() + fm.getLeading(),
                getWidth(), (lastLine - firstLine + 1) * fm.getFontHeight());
    }

    /**
     * Repaints the lines containing the selection.
     */
    public final void invalidateSelectedLines() {
        invalidateLineRange(textArea.getSelectionStartLine(),
                textArea.getSelectionEndLine());
    }

    /**
     * Implementation of TabExpander interface. Returns next tab stop after
     * a specified point.
     *
     * @param x         The x co-ordinate
     * @param tabOffset Ignored
     * @return The next tab stop after <i>x</i>
     */
    public float nextTabStop(float x, int tabOffset) {
        int offset = textArea.getHorizontalOffset();
        if (tabSize == 0) {
            tabSize = 4;
        }
        int ntabs = ((int) x - offset) / tabSize;
        return (ntabs + 1) * tabSize + offset;
    }

    /**
     * Returns the painter's preferred size.
     */
    public Dimension getPreferredSize() {
        Dimension dim = new Dimension();
        //dim.width = fm.charWidth('w') * maxColumn;
        int width = getMaximumLineLength();
        dim.width = width;

        //System.out.println("Max Col = "  + text.length() + ",  width = " + dim.width);
        if (rows != 25) {
            dim.height = fm.getFontHeight() * rows;
        }
        else {
            dim.height = fm.getFontHeight() * textArea.getLineCount();
        }
        return dim;
    }

    public int getMaximumLineLength() {
        int maxLengthLine = textArea.getMaxLengthLine();
        if (cols != 0 && cols > maxLengthLine) {
            int width = fm.charWidth('w') * cols;
            return width;
        }
        else {
            String text = textArea.getLineText(maxLengthLine);
            int width = fm.stringWidth(text);
            return width;
        }
    }


    /**
     * Returns the painter's minimum size.
     */
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    public boolean isShowCRLF() {
        return showCRLF;
    }

    public void setShowCRLF(boolean showCRLF) {
        this.showCRLF = showCRLF;
        if (showCRLF) {
            specialCharPainter = new SyntaxUtilities.SpecialCharPainterImpl(specialCharColor);
        }
        else {
            specialCharPainter = null;
        }
    }

    public SyntaxUtilities.SpecialCharPainter getSpecialCharPainter() {
        return specialCharPainter;
    }

    // package-private members
    int currentLineIndex;
    Token currentLineTokens;
    Segment currentLine;
    SyntaxUtilities.SpecialCharPainter specialCharPainter;
    // protected members
    protected JEditTextArea textArea;

    protected SyntaxStyle[] styles;
    protected Color caretColor;
    protected Color selectionBackgroundColor;
    protected Color selectionForegroundColor;
    protected Color lineHighlightColor;
    protected Color bracketHighlightColor;
    protected Color eolMarkerColor;

    protected boolean blockCaret;
    protected boolean lineHighlight;
    protected boolean bracketHighlight;
    protected boolean paintInvalid = false;
    protected boolean eolMarkers = false;
    protected boolean showCRLF = false;

    private int simpleCaretWidth = 2;
    protected int cols;
    protected int rows;
    protected int interLineSpacing;
    protected int tabSize;
    //protected FontMetrics fontMetrics;
    FontMetrics2 fm;
    protected Highlight highlights;

    protected void paintLine(Graphics gfx, TokenMarker tokenMarker, int line, int x) {
        //Font defaultFont = getFont();
        LogicalFont defaultFont = logicalFont;
        Color defaultColor = getForeground();

        currentLineIndex = line;
        int y = textArea.lineToY(line);

        if (line < 0 || line >= textArea.getLineCount()) {
            if (paintInvalid) {
                paintHighlight(gfx, line, y);
                styles[Token.INVALID].setGraphicsFlags(gfx, defaultFont);
                gfx.drawString("~", 0, y + fm.getHeight());
            }
        }
        //CORE-3089 Paint text based on syntaxHighlight flag
        else if (tokenMarker == null || !syntaxHighlight) {
            paintPlainLine(gfx, line, defaultFont, defaultColor, x, y);
        }
        else {
            paintSyntaxLine(gfx, tokenMarker, line, defaultFont, defaultColor, x, y);
        }
    }


    protected void paintPlainLine(Graphics gfx, int line, LogicalFont defaultFont,
                                  Color defaultColor, int x, int y) {
        paintHighlight(gfx, line, y);
        textArea.getLineText(line, currentLine);

        //gfx.setFont(defaultFont);
        gfx.setFont(defaultFont.getPrimaryFont());
        gfx.setColor(defaultColor);

        y += fm.getHeight();
        x = SyntaxUtilities.drawTabbedText(currentLine, x, y, gfx, defaultFont, this, 0, specialCharPainter);
        if (eolMarkers) {
            gfx.setColor(eolMarkerColor);
            gfx.drawString(".", x, y);
        }
    }
    protected void paintSelectedLine(Graphics gfx, int line, LogicalFont defaultFont,
                                     int x, int y) {
        paintHighlight(gfx, line, y);
        textArea.getLineText(line, currentLine);

        //gfx.setFont(defaultFont);
        gfx.setFont(defaultFont.getPrimaryFont());
        gfx.setColor(selectionForegroundColor);

        y += fm.getHeight();
        x = SyntaxUtilities.drawTabbedText(currentLine, x, y, gfx, defaultFont, this, 0, specialCharPainter);
        if (eolMarkers) {
            gfx.setColor(eolMarkerColor);
            gfx.drawString(".", x, y);
        }
    }

    protected void paintSyntaxLine(Graphics gfx, TokenMarker tokenMarker, int line, LogicalFont defaultFont, Color defaultColor, int x, int y) {
        textArea.getLineText(currentLineIndex, currentLine);
        currentLineTokens = tokenMarker.markTokens(SegmentLine.create(currentLine), currentLineIndex);

        paintHighlight(gfx, line, y);

        //gfx.setFont(defaultFont);
        gfx.setFont(defaultFont.getPrimaryFont());
        gfx.setColor(defaultColor);
        y += fm.getHeight();
        x = SyntaxUtilities.paintSyntaxLine(currentLine, currentLineTokens, styles, this, gfx, defaultFont, x, y, specialCharPainter);

        if (eolMarkers) {
            gfx.setColor(eolMarkerColor);
            gfx.drawString(".", x, y);
        }
    }

    protected void paintHighlight(Graphics gfx, int line, int y) {

        if (line >= textArea.getSelectionStartLine()
                && line <= textArea.getSelectionEndLine()) {
            paintLineHighlight(gfx, line, y);
        }

        if (highlights != null) {
            highlights.paintHighlight(gfx, line, y);
        }

        if (bracketHighlight && line == textArea.getBracketLine()) {
            paintBracketHighlight(gfx, line, y);
        }

        if (line == textArea.getCaretLine()) {
            paintCaret(gfx, line, y);
        }
    }

    protected void paintLineHighlight(Graphics gfx, int line, int y) {
        //int height = fm.getHeight();
        int height = fm.getFontHeight();
        y += fm.getLeading() + fm.getMaxDescent();

        int selectionStart = textArea.getSelectionStart();
        int selectionEnd = textArea.getSelectionEnd();

        if (selectionStart == selectionEnd) {
            if (lineHighlight) {
                gfx.setColor(lineHighlightColor);
                gfx.fillRect(0, y, getWidth(), height);
            }
        }
        else {
            gfx.setColor(selectionBackgroundColor);

            int selectionStartLine = textArea.getSelectionStartLine();
            int selectionEndLine = textArea.getSelectionEndLine();
            int lineStart = textArea.getLineStartOffset(line);

            int x1, x2;
            if (textArea.isSelectionRectangular()) {
                int lineLen = textArea.getLineLength(line);
                x1 = textArea._offsetToX(line, Math.min(lineLen,
                        selectionStart - textArea.getLineStartOffset(selectionStartLine)));
                x2 = textArea._offsetToX(line, Math.min(lineLen,
                        selectionEnd - textArea.getLineStartOffset(selectionEndLine)));
                if (x1 == x2) {
                    x2++;
                }
            }
            else if (selectionStartLine == selectionEndLine) {
                x1 = textArea._offsetToX(line,
                        selectionStart - lineStart);
                x2 = textArea._offsetToX(line,
                        selectionEnd - lineStart);
            }
            else if (line == selectionStartLine) {
                x1 = textArea._offsetToX(line,
                        selectionStart - lineStart);
                x2 = getWidth();
            }
            else if (line == selectionEndLine) {
                x1 = 0;
                x2 = textArea._offsetToX(line,
                        selectionEnd - lineStart);
            }
            else {
                x1 = 0;
                x2 = getWidth();
            }

            // "inlined" min/max()
            gfx.fillRect(x1 > x2 ? x2 : x1, y, x1 > x2 ?
                    (x1 - x2) : (x2 - x1), height);
        }

    }


    protected void paintBracketHighlight(Graphics gfx, int line, int y) {
        int position = textArea.getBracketPosition();
        if (position == -1) {
            return;
        }
        y += fm.getLeading() + fm.getMaxDescent();
        int x = textArea._offsetToX(line, position);
        gfx.setColor(bracketHighlightColor);
        // Hack!!! Since there is no fast way to get the character
        // from the bracket matching routine, we use ( since all
        // brackets probably have the same width anyway
        gfx.drawRect(x, y, fm.charWidth('(') - 1,
                fm.getHeight() - 1);
    }

    protected void paintCaret(Graphics gfx, int line, int y) {
        if (textArea.isCaretVisible()) {
            int offset = textArea.getCaretPosition()
                    - textArea.getLineStartOffset(line);
            int caretX = textArea.offsetToX(line, offset);
            int caretWidth = ((blockCaret ||
                    textArea.isOverwriteEnabled()) ?
                    fm.charWidth('w') : simpleCaretWidth);
            y += fm.getLeading() + fm.getMaxDescent();
            int height = fm.getHeight();

            gfx.setColor(caretColor);

            if (textArea.isOverwriteEnabled()) {
                gfx.fillRect(caretX, y + height - 1,
                        caretWidth, 1);
            }
            else {
                gfx.drawRect(caretX, y, caretWidth - 1, height - 1);
            }
        }
    }

    //CORE-4456 Provided accessibility support for TextAreaPainter
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


