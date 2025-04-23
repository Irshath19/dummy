/*
*  FormulaTextArea.java
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
package com.tplus.transform.design.formula.ui;

import com.tplus.transform.design.formula.*;
import com.tplus.transform.design.formula.ast.*;
import com.tplus.transform.design.formula.util.FunctionStateHelper;
import com.tplus.transform.design.formula.util.PrettyPrintOptions;
import com.tplus.transform.design.formula.util.PrettyPrinter;
import com.tplus.transform.design.ui.FormulaDesignContextProvider;
import com.tplus.transform.design.ui.FormulaDesignContext;
import com.tplus.transform.design.CodeType;
import com.tplus.transform.design.ui.table.FormulaEditorHelper;
import com.tplus.transform.swing.ExtendedAction;
import com.tplus.transform.swing.JHTMLTextLabel;
import com.tplus.transform.swing.SimpleLineBorder;
import com.tplus.transform.swing.help.HelpManager;
import com.tplus.transform.swing.text.AutoComplete;
import com.tplus.transform.swing.text.JEditTextArea;
import com.tplus.transform.swing.text.TextEditComponent;
import com.tplus.transform.swing.text.highlight.*;
import com.tplus.transform.swing.text.marker.FormulaTokenMarker;
import com.tplus.transform.swing.text.marker.FunctionNameResolver;
import com.tplus.transform.swing.text.marker.XPathTokenMarker;
import com.tplus.transform.util.StringUtils;
import com.tplus.transform.util.Location;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.net.URL;
import java.util.*;
import java.util.List;

/**
 * User: Krishnan
 * Date: 07-Mar-2005
 * Time: 14:04:10
 * <p/>
 * � Copyright 2001-2003 Volante Technologies, Inc.
 * All rights reserved.
 * <p/>
 * This software is the confidential and proprietary information of Volante
 * Technologies Inc. Your use of this software is governed by the terms of the
 * license agreement and/or any nondisclosure agreements you have entered
 * into with Volante.  This software may not be disseminated, distributed
 * or otherwise disclosed without the prior, written permission of Volante.
 */

public class FormulaTextArea extends JEditTextArea {
    protected static final Object ERROR_TYPE = new Object();
    public static final Object DEPRECATE_TYPE = new Object();
    static final Object SELECTION_TYPE = new Object();
    static final Object BOX_TYPE = new Object();
    static Map highlightTypeVsHighlighter = new HashMap();
    CodeAST codeAST;

    static {
        highlightTypeVsHighlighter.put(ERROR_TYPE, new ErrorHighlighter());
        highlightTypeVsHighlighter.put(SELECTION_TYPE, new SelectionHighlighter());
        highlightTypeVsHighlighter.put(BOX_TYPE, new BoxHighlighter());
        //CORE-3092 Deprecate Highlighter for FormulaTextEditor
        highlightTypeVsHighlighter.put(DEPRECATE_TYPE, new DeprecateHighlighter());
    }

    FunctionHyperLinkHighlightImpl functionHyperLinkHighlight;
    FieldHyperLinkHighlightImpl fieldHyperLinkHighlight;
    MultiLocationHighlight locationHighlight;
    private FunctionManager functionManager;
    private CodeType codeType = CodeType.FORMULA;
    private FormulaDesignContextProvider formulaDesignContextProvider;

    public FormulaTextArea(FormulaDesignContextProvider formulaDesignContextProvider) {
        this(FunctionManagerFactory.getFunctionManager(), CodeType.FORMULA);
        this.formulaDesignContextProvider = formulaDesignContextProvider;
    }

    public FormulaTextArea(FormulaDesignContext formulaDesignContext) {
        this(formulaDesignContext.getFunctionManager(), formulaDesignContext.getCodeType());
    }

    public FormulaTextArea(FunctionManager functionManager, CodeType codeType) {
        this.functionManager = functionManager;
        this.codeType = codeType;
        init();
    }

    public FormulaDesignContextProvider getFormulaDesignContextProvider() {
        return formulaDesignContextProvider;
    }

    public void setText(String text) {
        if (locationHighlight != null) {
            locationHighlight.clear();
        }
        super.setText(text);
    }

    public void setFormulaDesignContext(FormulaDesignContext formulaDesignContext) {
        if (formulaDesignContext == null) {
            this.functionManager = FunctionManagerFactory.getFunctionManager();
        }
        else {
            //this.functionManager =
            setFunctionManager(formulaDesignContext.getFunctionManager());
            codeType = formulaDesignContext.getCodeType();
            setTokenMarker(nameResolver);
        }
    }

    public FunctionManager getFunctionManager() {
        return functionManager;
    }

    public void setFunctionManager(FunctionManager functionManager) {
        this.functionManager = functionManager;
        functionHyperLinkHighlight.setFunctionManager(functionManager);
    }

    public MultiLocationHighlight getLocationHighlight() {
        return locationHighlight;
    }

    public void setBackground(Color clr) {
        super.setBackground(clr);
        painter.setBackground(clr);
    }

    class ComposerFunctionNameResolver implements FunctionNameResolver {

        public ComposerFunctionNameResolver() {
        }

        public boolean isValid(String name) {
            return functionManager.getFunctionByName(name) != null;
        }

        public boolean isValid(char[] array, int offset, int length) {
            return isValid(new String(array, offset, length));
        }
    }

    ExtendedAction editFormulaAction = new ExtendedAction("Edit Formula ...", "/images/edit-formula.png") {
        {
            setAccelerator("F4");
            setHelpId("Edit Formula");
        }
        public void actionPerformed(ActionEvent actionEvent) {
            fireEditDialog(actionEvent.getSource());
        }
    };
    public void fireEditDialog(Object source) {
        Action editFormulaAction = getActionMap().get(FormulaEditorHelper.EDIT_FORMULA_ACTION_NAME);
        if (editFormulaAction != null) {
            editFormulaAction.actionPerformed(new ActionEvent(this, 100, FormulaEditorHelper.EDIT_FORMULA_ACTION_NAME));
        }
    }

    public ExtendedAction getEditFormulaAction() {
        return editFormulaAction;
    }

    FunctionNameResolver nameResolver = new ComposerFunctionNameResolver();

    void init() {
        addContextAction(0,null);
        addContextAction(0,getEditFormulaAction());
        setTokenMarker(nameResolver);
        setTabSize(4);
        setInterLineSpacing(3);
        //SyntaxUtilities.SpecialCharPainter charPainter = getPainter().getSpecialCharPainter();
        //if (charPainter != null)
        //    charPainter.setMaxPrintableChar(Character.MAX_VALUE);

        functionHyperLinkHighlight = new FunctionHyperLinkHighlightImpl(functionManager);
        fieldHyperLinkHighlight = new FieldHyperLinkHighlightImpl();
        locationHighlight = new MultiLocationHighlight(highlightTypeVsHighlighter);
        getPainter().addCustomHighlight(fieldHyperLinkHighlight);
        getPainter().addCustomHighlight(functionHyperLinkHighlight);
        getPainter().addCustomHighlight(locationHighlight);
        getInputHandler().addKeyBinding("T+SPACE", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                new AutoCompleteFunctions(functionManager, FormulaTextArea.this).show();
            }
        });
        getInputHandler().addKeyBinding("C+W", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                selectASTExpression();
            }
        });
        getInputHandler().addKeyBinding("CS+W", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                reverseSelectASTExpression();
            }
        });

        getInputHandler().addKeyBinding("C+R", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showRefactorings();
            }
        });
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_F1) {
                    String word = null;
                    int selectionStart = getSelectionStart();
                    int selectionEnd = getSelectionEnd();
                    if (selectionStart == selectionEnd) {
                        int line = getLineOfOffset(selectionStart);
                        int lineStartOffset = getLineStartOffset(line);
                        String lineText = getLineText(line);
                        int offset = selectionStart - lineStartOffset;
                        int wordStart = HyperLinkHighlight.getWordStart(lineText, offset);
                        int wordEnd = HyperLinkHighlight.getWordEnd(lineText, offset);
                        if (wordStart < wordEnd) {
                            word = lineText.substring(wordStart, wordEnd);
                        }
                    }
                    else {
                        word = getSelectedText();
                    }
                    if (word != null && word.length() > 0) {
                        HelpManager.getHelpManager().showHelp(word);
                        e.consume();
                    }
                }
            }
        });
    }

    /**
     * Selects the current code block or container at the cursor position
     */
    private void reverseSelectASTExpression() {
        CodeNode toSelNode = getSelectedNode();
        if(anchorLocation != null) {
            CodeNode tempNode = getCodeAST().findNodeAtLocation(anchorLocation, anchorLocation);
            Location startLocation;
            Location endLocation;
            if(toSelNode == null) {
                if(tempNode != null) {
                    if (tempNode.getParentNode() != null) {
                        while (tempNode.getParentNode().getParentNode() != null) {
                            tempNode = tempNode.getParentNode();
                        }
                    }
                    toSelNode = tempNode;
                    startLocation = toSelNode.getStartLocation();
                    endLocation = toSelNode.getEndLocation();
                } else startLocation = endLocation = anchorLocation;
            } else {
                if(toSelNode.equals(tempNode)) {
                    startLocation = endLocation = anchorLocation;
                } else {
                    while (!toSelNode.equals(tempNode.getParentNode())) {
                        tempNode = tempNode.getParentNode();
                    }
                    toSelNode = tempNode;
                    startLocation = toSelNode.getStartLocation();
                    endLocation = toSelNode.getEndLocation();
                }
            }
            int toSelStart = getOffsetFromLocation(startLocation);
            int toSelEnd = getOffsetFromLocation(endLocation);
            select(toSelStart, toSelEnd);
        }
    }
    private void setTokenMarker(FunctionNameResolver nameResolver) {
        if (codeType == CodeType.FORMULA) {
            FormulaTokenMarker formulaTokenMarker = new FormulaTokenMarker();
            formulaTokenMarker.setFunctionNameResolver(nameResolver);
            setTokenMarker(formulaTokenMarker);
        }
        if (codeType == CodeType.XPATH) {
            XPathTokenMarker xPathTokenMarker = new XPathTokenMarker();
            xPathTokenMarker.setFunctionNameResolver(nameResolver);
            setTokenMarker(xPathTokenMarker);
        }
    }

    private void showRefactorings() {
        if (codeAST instanceof FormulaAST) {
            final FormulaAST temp = (FormulaAST) codeAST;
            final FormulaNode node = (FormulaNode) getSelectedNode();
            if (node != null) {
                List refactorings = RefactoringFactory.getRefactoringFactory().getRefactorings(node);
                JPopupMenu popupMenu = new JPopupMenu();
                for (Object refactoring1 : refactorings) {
                    final Refactoring refactoring = (Refactoring) refactoring1;
                    popupMenu.add(new ExtendedAction(refactoring.getName(), null) {
                        public void actionPerformed(ActionEvent event) {
                            refactoring.execute(node);
                            PrettyPrinter prettyPrinter = new PrettyPrinter(functionManager, new PrettyPrintOptions());
                            String output = prettyPrinter.prettyPrint(temp);
                            selectAll();
                            replaceSelection(output);
                            //System.out.println(output);
                        }
                    });
                }
                int x = _offsetToX(getCaretLine(), getCaretPosition() - getLineStartOffset(getCaretLine()));
                int y = lineToY(getCaretLine());
                popupMenu.show(this, x, y + 20);
            }
        }
    }

    CodeNode getSelectedNode() {
        if (codeAST != null) {
            CodeAST temp = codeAST;
            Location startLocation = getSelectionStartLocation();
            Location endLocation = getSelectionEndLocation();
            CodeNode toSelNode = temp.findNodeAtLocation(startLocation, endLocation);
            return toSelNode;
        }
        return null;
    }


    private void selectASTExpression() {
        if(anchorLocation == null) {
            anchorLocation = getLocation(getCaretPosition());
        }
        CodeNode toSelNode = getSelectedNode();
        if (toSelNode != null) {
            Location startLocation = getSelectionStartLocation();
            Location endLocation = getSelectionEndLocation();
            if (startLocation.equals(toSelNode.getStartLocation()) && endLocation.equals(toSelNode.getEndLocation())) {
                toSelNode = toSelNode.getParentNode();
            }
        }
        if (toSelNode != null) {
            Location startLocation = toSelNode.getStartLocation();
            Location endLocation = toSelNode.getEndLocation();
            int toSelStart = getOffsetFromLocation(startLocation);
            int toSelEnd = getOffsetFromLocation(endLocation);
            select(toSelStart, toSelEnd);
        }
    }

    public int getOffsetFromLocation(Location startLocation) {
        return getLineStartOffset(startLocation.getLine() - 1) + startLocation.getColumn() - 1;
    }

    public CodeAST getCodeAST() {
        return codeAST;
    }

    public void setCodeAST(CodeAST formulaAST) {
        this.codeAST = formulaAST;
    }

    public void addTextRefClickListener(TextRefClickListener textRefClickListener) {
        functionHyperLinkHighlight.addTextRefClickListener(textRefClickListener);
        fieldHyperLinkHighlight.addTextRefClickListener(textRefClickListener);
    }

    public void setInterLineSpacing(int spacing) {
        painter.setInterLineSpacing(spacing);

    }

    public Location getSelectionEndLocation() {
        return getLocation(getSelectionEnd());
    }

    public Location getSelectionStartLocation() {
        return getLocation(getSelectionStart());
    }


    class FieldHyperLinkHighlightImpl extends HyperLinkHighlight {

        FieldHyperLinkHighlightImpl() {
        }

        protected HighlightInfo getHighlightInfo(JEditTextArea jEditTextArea, String lineText, int line, int lineStartOffset, int offset) {
            CodeAST tempAST = codeAST;
            if (tempAST != null) {
                int textAreaoffset = jEditTextArea.getOffset(line, offset);
                Location loc = getLocation(textAreaoffset);
                VariableRefInfo variableRefInfo = codeAST.getRefAtlocation(loc);
                if (variableRefInfo != null) {
                    int startOffset = getOffset(variableRefInfo.getStartLocation());
                    int endOffset = getOffset(variableRefInfo.getEndLocation());
                    VariableInfo variableInfo = variableRefInfo.getVariableInfo();
                    if (variableInfo != null) {
                        FieldRefInfo refInfo = new FieldRefInfo(variableInfo);
                        return new HighlightInfo(startOffset, endOffset, refInfo);
                    }
                }
                /*
                CodeNode node = tempAST.findNodeAtLocation(loc);
                if (node instanceof FieldAccessNode) {
                    FieldAccessNode fieldAccessNode = (FieldAccessNode) node;
                    LocationRange startLoc = fieldAccessNode.getStartLocation();
                    LocationRange endLoc = fieldAccessNode.getEndLocation();
                    int startOffset = getOffset(startLoc);
                    int endOffset = getOffset(endLoc);
                    VariableInfo variableInfo = fieldAccessNode.getVariableInfo();
                    if (variableInfo != null) {
                        FieldRefInfo refInfo = new FieldRefInfo(variableInfo);
                        return new HighlightInfo(startOffset, endOffset, refInfo);
                    }
                    //new TextRefInfo()
                }*/
            }
            return null;
        }

        protected TextRefInfo getTextRefInfo(String word) {
            return null;
        }
    }


}

class FunctionHyperLinkHighlightImpl extends HyperLinkHighlight {
    private FunctionManager functionManager;

    FunctionHyperLinkHighlightImpl(FunctionManager functionManager) {
        this.functionManager = functionManager;
    }

    public FunctionManager getFunctionManager() {
        return functionManager;
    }

    public void setFunctionManager(FunctionManager functionManager) {
        this.functionManager = functionManager;
    }

    protected TextRefInfo getTextRefInfo(String word) {
        FunctionConfig functionConfig = functionManager.getFunctionByName(word);
        if (functionConfig != null) {
            return new FunctionRefInfo(word, functionConfig);
        }
        return null;
    }
}


class AutoCompleteFunctions extends AutoComplete {
    private FunctionManager functionManager;
    JPanel descPane;
    private JHTMLTextLabel signLabel;
    //private JTextLabel descLabel;
    DefaultListCellRenderer cellRenderer;

    public AutoCompleteFunctions(FunctionManager functionManager, TextEditComponent editComponent) {
        super(editComponent);
        this.functionManager = functionManager;
    }

    protected JPanel prepareContentPane(JComponent mainListCmp) {
        JPanel contentPane = super.prepareContentPane(mainListCmp);
        prepareDescPanel();

        contentPane.add(descPane, BorderLayout.SOUTH);
        list.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) {
                    return;
                }
                updateDescription();
            }
        });
        cellRenderer = new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof FunctionConfig) {
                    FunctionConfig config = (FunctionConfig) value;
                    String toSet = getSignatureHTML(config, isSelected);
                    setText(toSet);
                }
                return this;
            }


        };
        list.setCellRenderer(cellRenderer);
        list.setPrototypeCellValue("12345678901234567890123456789012345678901234567890");
        updateDescription();
        return contentPane;
    }

    private void updateDescription() {
        FunctionConfig config = (FunctionConfig) list.getSelectedValue();
        if (config != null) {
            String signature = StringUtils.fixNull(config.getSignature());
            signature = StringUtils.fixNull(signature);
            signLabel.setText(signature);
            String desc = config.getDescription();
            if (desc != null) {
                desc = StringUtils.leftStr(desc, ".") + ".";
            }
            int bracIndex = signature.indexOf('(');
            String toSet = "<html><body>" +
                    "<b>" + signature /*signature.substring(0, bracIndex) + signature.substring(bracIndex)*/ + "</b>" +
                    "&nbsp;<a href='http://localhost/name'>" + "Help" + "</a>" +

                    "<br>" +

                    "<b>" + "Category: " + "</b>" + "<a href='http://localhost/category'>" + config.getCategory() + "</a><br>" +
                    desc +
                    "</body></html>";
            //descLabel.setText(desc);
            signLabel.setText(toSet);
        }
    }

    String getSignatureHTML(FunctionConfig config, boolean isSelected) {
        String signature = StringUtils.fixNull(config.getSignature());
        int bracIndex = signature.indexOf('(');
        String sign;
        //CORE-1550 - Highlight the deprecated/beta functions
        String functionState = config.getFunctionState();
        boolean isFunctionStateAvailable = functionState != null;
        if (bracIndex != -1) {
            sign = highlightName(signature.substring(0, bracIndex), isSelected, isFunctionStateAvailable) +
                    signature.substring(bracIndex);
        }
        else {
            sign = signature;
        }
        sign = FunctionStateHelper.getHTMLText(functionState, sign, FunctionStateHelper.STYLE_WITH_DESC);
        return "<html><body>" + sign + "</body></html>";
    }

    private String highlightName(String s, boolean selected, boolean isFunctionStateAvailable) {
        if (selected) {
            return "<b>" + s + "</b>";
        } else if(isFunctionStateAvailable){//CORE-1550 - Highlight the deprecated/beta functions
            return s;
        } else {
            return "<font color='blue'>" + s + "</font>";
        }
    }

    private void prepareDescPanel() {
        descPane = new JPanel();
        descPane.setBackground(UIManager.getColor("ToolTip.background"));
        descPane.setLayout(new BorderLayout());
        //descPane.setBorder(new LineBorder(Color.GRAY));
        signLabel = new JHTMLTextLabel();
        //descLabel = new JTextLabel(2, 30);
        descPane.setBorder(new CompoundBorder(
                new EmptyBorder(0, 2, 2, 2),
                new SimpleLineBorder(SimpleLineBorder.TOP, Color.GRAY)));
        descPane.add(signLabel, BorderLayout.CENTER);
        signLabel.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    URL url = e.getURL();
                    showHelp(url.getFile());
                }
            }

            private void showHelp(String prop) {
                FunctionConfig config = (FunctionConfig) list.getSelectedValue();
                if (config != null) {
                    if (prop.equals("/name")) {
                        showFunctonHelp(config.getName());
                    }
                    if (prop.equals("/category")) {
                        showCategoryHelp(config.getCategory());
                    }
                }
            }
        });
        signLabel.setText("a<br>a<br>a<br>a");
        descPane.setPreferredSize(descPane.getPreferredSize());

        //signLabel.setFont(signLabel.getFont().deriveFont(Font.BOLD));
        //descPane.add(descLabel, BorderLayout.CENTER);
    }

    private void showFunctonHelp(String name) {
        HelpManager.getHelpManager().showHelp(name);
    }

    private void showCategoryHelp(String name) {
        HelpManager.getHelpManager().showHelp(name + " Functions");
    }


    private List getFunctions(String word) {
        FunctionConfig[] functions = functionManager.getAllFunctions();
        List toShow = new ArrayList();
        //String lastFunc = null;
        if (word.length() > 0) {
            String wordLower = word.toLowerCase();
            for (FunctionConfig function : functions) {
                String name = function.getName().toLowerCase();
                /**
                 * CORE-927
                 * To display overloaded methods in Formula pane.
                 * */
                if (name.startsWith(wordLower)) {
                    /*if (name.equals(lastFunc)) {
                        continue;
                    }*/
                    toShow.add(function);
                    //lastFunc = name;
                }
            }
        }
        else {
            toShow.addAll(Arrays.asList(functions));
        }
        return toShow;
    }

    protected java.util.List getMatchList(String word) {
        return getFunctions(word);
    }

    protected String getInsertionText(Object match) {
        FunctionConfig selectedFormula = (FunctionConfig) match;
        if (StringUtils.fixNull(selectedFormula.getSignature()).contains("(")) {
            return selectedFormula.getName() + "(";
        }
        else {
            return selectedFormula.getName();
        }
    }
/*
    public String getFunctionNameXPath(FunctionConfig function) {
        String name = function.getName();
        String uri = function.getClassName();
        NamespaceManager namespaceManager = formulaDesignContext.getNamespaceManager();
        String prefix = namespaceManager.getPrefix(uri);
        if(prefix == null) {
            prefix="unknown";
        }
        return prefix + ":" + name;
    }*/

}
