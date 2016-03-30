/*
 * Copyright 2016 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.rf.ide.core.testdata.text.write;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.rf.ide.core.testdata.model.AModelElement;
import org.rf.ide.core.testdata.model.FilePosition;
import org.rf.ide.core.testdata.model.ModelType;
import org.rf.ide.core.testdata.model.RobotFile;
import org.rf.ide.core.testdata.model.RobotVersion;
import org.rf.ide.core.testdata.model.table.ARobotSectionTable;
import org.rf.ide.core.testdata.model.table.TableHeader;
import org.rf.ide.core.testdata.text.read.EndOfLineBuilder;
import org.rf.ide.core.testdata.text.read.EndOfLineBuilder.EndOfLineTypes;
import org.rf.ide.core.testdata.text.read.IRobotLineElement;
import org.rf.ide.core.testdata.text.read.IRobotTokenType;
import org.rf.ide.core.testdata.text.read.LineReader.Constant;
import org.rf.ide.core.testdata.text.read.RobotLine;
import org.rf.ide.core.testdata.text.read.VersionAvailabilityInfo;
import org.rf.ide.core.testdata.text.read.recognizer.RobotToken;
import org.rf.ide.core.testdata.text.read.recognizer.RobotTokenType;
import org.rf.ide.core.testdata.text.read.separators.Separator;
import org.rf.ide.core.testdata.text.read.separators.Separator.SeparatorType;
import org.rf.ide.core.testdata.text.write.SectionBuilder.Section;

import com.google.common.base.Optional;

public class DumperHelper {

    private static final int NUMBER_OF_AFTER_UNIT_ELEMENTS_TO_TREAT_AS_NEW_UNIT_START = 3;

    // private static final int MAX_NUMBER_OF_COLUMN_IN_LINE = 7;
    //
    // private static final int MAX_NUMBER_OF_CHARS_IN_LINE = 120;

    private static final String EMPTY = "\\";

    private final ARobotFileDumper currentDumper;

    public DumperHelper(final ARobotFileDumper currentDumper) {
        this.currentDumper = currentDumper;
    }

    public void addEOFinCaseIsMissing(final RobotFile model, final List<RobotLine> lines) {
        IRobotLineElement buildEOL = new EndOfLineBuilder()
                .setEndOfLines(Arrays.asList(new Constant[] { Constant.EOF })).buildEOL();

        if (lines.isEmpty()) {
            updateLine(model, lines, buildEOL);
        } else {
            RobotLine robotLine = lines.get(lines.size() - 1);
            if (robotLine.getEndOfLine() == null || robotLine.getEndOfLine().getFilePosition().isNotSet()) {
                updateLine(model, lines, buildEOL);
            }
        }
    }

    public Separator getSeparator(final RobotFile model, final List<RobotLine> lines, final IRobotLineElement lastToken,
            final IRobotLineElement currentToken) {
        return currentDumper.getSeparator(model, lines, lastToken, currentToken);
    }

    public void updateLine(final RobotFile model, final List<RobotLine> outLines, final IRobotLineElement elem) {
        if (isEndOfLine(elem)) {
            if (outLines.isEmpty()) {
                RobotLine line = new RobotLine(1, model);
                line.setEndOfLine(Constant.get(elem), 0, 0);
                outLines.add(line);
            } else {
                RobotLine line = outLines.get(outLines.size() - 1);
                final FilePosition pos = getPosition(line, outLines);
                line.setEndOfLine(Constant.get(elem), pos.getOffset(), pos.getColumn());
            }

            if (!elem.getTypes().contains(EndOfLineTypes.EOF)) {
                outLines.add(new RobotLine(outLines.size() + 1, model));
            }
        } else {
            final RobotLine line;
            if (outLines.isEmpty()) {
                line = new RobotLine(1, model);
                outLines.add(line);
            } else {
                line = outLines.get(outLines.size() - 1);
            }

            final IRobotLineElement artToken = cloneWithPositionRecalculate(elem, line, outLines);
            if (elem instanceof Separator) {
                if (line.getLineElements().isEmpty() && artToken.getTypes().contains(SeparatorType.PIPE)) {
                    Separator elemSep = (Separator) artToken;
                    int pipeIndex = elemSep.getRaw().indexOf('|');
                    if (pipeIndex >= 1 && !(pipeIndex == 1 && elemSep.getRaw().charAt(0) == ' ')) {
                        elemSep.setRaw(elemSep.getRaw().substring(pipeIndex));
                        elemSep.setText(elemSep.getRaw());
                    }
                }
            }

            // if (elem.getTypes().contains(RobotTokenType.VARIABLES_VARIABLE_VALUE) ||
            // elem.getTypes().contains(RobotTokenType.)) {
            if (elem instanceof RobotToken) {
                if (artToken.isDirty()) {
                    if (artToken.getRaw().isEmpty()) {
                        if (artToken instanceof RobotToken) {
                            RobotToken rt = (RobotToken) artToken;
                            rt.setRaw(getEmpty());
                            rt.setText(getEmpty());
                        }
                    } else {
                        if (artToken instanceof RobotToken) {
                            RobotToken rt = (RobotToken) artToken;
                            String text = formatWhiteSpace(rt.getText());
                            rt.setRaw(text);
                            rt.setText(text);
                        }
                    }
                }
            }
            // }

            line.addLineElement(cloneWithPositionRecalculate(artToken, line, outLines));
        }
    }

    public void dumpHeader(final RobotFile model, final TableHeader<? extends ARobotSectionTable> th,
            final List<RobotLine> lines) {
        if (!lines.isEmpty()) {
            RobotLine lastLine = lines.get(lines.size() - 1);
            IRobotLineElement endOfLine = lastLine.getEndOfLine();
            if ((endOfLine == null || endOfLine.getFilePosition().isNotSet()
                    || endOfLine.getTypes().contains(EndOfLineTypes.NON)
                    || endOfLine.getTypes().contains(EndOfLineTypes.EOF)) && !lastLine.getLineElements().isEmpty()) {
                final IRobotLineElement lineSeparator = getLineSeparator(model);
                updateLine(model, lines, lineSeparator);
            }
        }

        final RobotToken decToken = th.getDeclaration();
        final FilePosition filePosition = decToken.getFilePosition();
        int fileOffset = -1;
        if (filePosition != null && !filePosition.isNotSet()) {
            fileOffset = filePosition.getOffset();
        }

        RobotLine currentLine = null;
        if (fileOffset >= 0) {
            Optional<Integer> lineIndex = model.getRobotLineIndexBy(fileOffset);
            if (lineIndex.isPresent()) {
                currentLine = model.getFileContent().get(lineIndex.get());
            }
        }

        boolean wasHeaderType = false;
        final RobotTokenType headerType = convertHeader(th.getModelType());
        final List<IRobotTokenType> tokenTypes = th.getDeclaration().getTypes();
        for (int index = 0; index < tokenTypes.size(); index++) {
            final IRobotTokenType tokenType = tokenTypes.get(index);
            if (RobotTokenType.isTableHeader(tokenType)) {
                if (headerType == tokenType) {
                    if (wasHeaderType) {
                        tokenTypes.remove(index);
                        index--;
                    }
                    wasHeaderType = true;
                } else {
                    tokenTypes.remove(index);
                    index--;
                }
            }
        }

        if (!wasHeaderType) {
            tokenTypes.clear();
            tokenTypes.add(headerType);
        }

        if ((decToken.getRaw() == null || decToken.getRaw().isEmpty())
                && (decToken.getText() == null || decToken.getText().isEmpty())) {
            final RobotVersion robotVersionInstalled = model.getParent().getRobotVersion();
            final VersionAvailabilityInfo vaiInCaseNoMatches = getTheMostCorrectOneRepresentation(headerType,
                    robotVersionInstalled);
            if (vaiInCaseNoMatches != null) {
                decToken.setRaw(vaiInCaseNoMatches.getRepresentation());
                decToken.setText(vaiInCaseNoMatches.getRepresentation());
            }
        } else if (decToken.getRaw() == null || decToken.getRaw().isEmpty()) {
            decToken.setRaw(decToken.getText());
        } else if (decToken.getText() == null || decToken.getText().isEmpty()) {
            decToken.setText(decToken.getRaw());
        }

        if (currentLine != null) {
            dumpSeparatorsBeforeToken(model, currentLine, decToken, lines);
        }

        updateLine(model, lines, decToken);
        IRobotLineElement lastToken = decToken;

        if (currentLine != null) {
            final List<IRobotLineElement> lineElements = currentLine.getLineElements();
            if (!decToken.isDirty()) {
                final int tokenPosIndex = lineElements.indexOf(decToken);
                if (lineElements.size() - 1 > tokenPosIndex + 1) {
                    final IRobotLineElement nextElem = lineElements.get(tokenPosIndex + 1);
                    if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                        updateLine(model, lines, nextElem);
                        lastToken = nextElem;
                    }
                }
            }

            for (final RobotToken columnToken : th.getColumnNames()) {
                dumpSeparatorsBeforeToken(model, currentLine, columnToken, lines);
                updateLine(model, lines, columnToken);
                lastToken = columnToken;
                if (!columnToken.isDirty()) {
                    int thisTokenPosIndex = lineElements.indexOf(decToken);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            for (final RobotToken commentPart : th.getComment()) {
                dumpSeparatorsBeforeToken(model, currentLine, commentPart, lines);
                updateLine(model, lines, commentPart);
                lastToken = commentPart;
                if (!commentPart.isDirty()) {
                    int thisTokenPosIndex = lineElements.indexOf(decToken);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            dumpSeparatorsAfterToken(model, currentLine, lastToken, lines);
            final IRobotLineElement endOfLine = currentLine.getEndOfLine();
            if (endOfLine != null) {
                final List<IRobotTokenType> types = endOfLine.getTypes();
                if (!types.contains(EndOfLineTypes.EOF) && !types.contains(EndOfLineTypes.NON)) {
                    updateLine(model, lines, endOfLine);
                }
            }
        }
    }

    private FilePosition getPosition(final RobotLine line, final List<RobotLine> outLines) {
        return getPosition(line, outLines, 1);
    }

    private FilePosition getPosition(final RobotLine line, final List<RobotLine> outLines, int last) {
        FilePosition pos = FilePosition.createNotSet();

        final IRobotLineElement endOfLine = line.getEndOfLine();
        if (endOfLine != null && !endOfLine.getFilePosition().isNotSet()) {
            pos = calculateEndPosition(endOfLine, true);
        } else if (!line.getLineElements().isEmpty()) {
            pos = calculateEndPosition(line.getLineElements().get(line.getLineElements().size() - 1), false);
        } else if (outLines != null && !outLines.isEmpty() && outLines.size() - last >= 0) {
            pos = getPosition(outLines.get(outLines.size() - last), outLines, last + 1);
        } else {
            pos = new FilePosition(1, 0, 0);
        }

        return pos;
    }

    private FilePosition calculateEndPosition(final IRobotLineElement elem, boolean isEOL) {
        final FilePosition elemPos = elem.getFilePosition();

        final String raw = elem.getRaw();
        int rawLength = 0;
        if (raw != null) {
            rawLength = raw.length();
        }

        int textLength = 0;
        final String text = elem.getText();
        if (text != null) {
            textLength = text.length();
        }

        final int dataLength = Math.max(rawLength, textLength);

        return new FilePosition(elemPos.getLine(), isEOL ? 0 : elemPos.getColumn() + dataLength,
                elemPos.getOffset() + dataLength);
    }

    public boolean isEndOfLine(final IRobotLineElement elem) {
        boolean result = false;
        for (final IRobotTokenType t : elem.getTypes()) {
            if (t instanceof EndOfLineTypes) {
                result = true;
                break;
            }
        }

        return result;
    }

    private IRobotLineElement cloneWithPositionRecalculate(final IRobotLineElement elem, final RobotLine line,
            final List<RobotLine> outLines) {
        IRobotLineElement newElem;
        if (elem instanceof RobotToken) {
            RobotToken newToken = new RobotToken();
            newToken.setLineNumber(line.getLineNumber());
            if (elem.getRaw().isEmpty()) {
                newToken.setRaw(elem.getText());
            } else {
                newToken.setRaw(elem.getRaw());
            }
            newToken.setText(elem.getText());
            if (!elem.getTypes().isEmpty()) {
                newToken.getTypes().clear();
            }
            newToken.getTypes().addAll(elem.getTypes());
            FilePosition pos = getPosition(line, outLines);
            newToken.setStartColumn(pos.getColumn());
            newToken.setStartOffset(pos.getOffset());

            newElem = newToken;
        } else {
            Separator newSeparator = new Separator();
            newSeparator.setType((SeparatorType) elem.getTypes().get(0));
            newSeparator.setLineNumber(line.getLineNumber());
            if (elem.getRaw().isEmpty()) {
                newSeparator.setRaw(elem.getText());
            } else {
                newSeparator.setRaw(elem.getRaw());
            }
            newSeparator.setText(elem.getText());
            if (!elem.getTypes().isEmpty()) {
                newSeparator.getTypes().clear();
            }
            newSeparator.getTypes().addAll(elem.getTypes());
            FilePosition pos = getPosition(line, outLines);
            newSeparator.setStartColumn(pos.getColumn());
            newSeparator.setStartOffset(pos.getOffset());

            newElem = newSeparator;
        }

        return newElem;
    }

    private String formatWhiteSpace(final String text) {
        String result = text;
        StringBuilder str = new StringBuilder();
        char lastChar = (char) -1;
        if (text != null) {
            char[] cArray = text.toCharArray();
            int size = cArray.length;
            for (int cIndex = 0; cIndex < size; cIndex++) {
                char c = cArray[cIndex];
                if (cIndex == 0) {
                    if (c == ' ') {
                        str.append("\\ ");
                    } else {
                        str.append(c);
                    }
                } else if (cIndex + 1 == size) {
                    if (c == ' ') {
                        str.append("\\ ");
                    } else {
                        str.append(c);
                    }
                } else {
                    if (lastChar == ' ' && c == ' ') {
                        str.append("\\ ");
                    } else {
                        str.append(c);
                    }
                }

                lastChar = c;
            }

            result = str.toString();
        }

        return result;
    }

    public RobotTokenType convertHeader(final ModelType modelType) {
        RobotTokenType type = RobotTokenType.UNKNOWN;
        if (modelType == ModelType.SETTINGS_TABLE_HEADER) {
            type = RobotTokenType.SETTINGS_TABLE_HEADER;
        } else if (modelType == ModelType.VARIABLES_TABLE_HEADER) {
            type = RobotTokenType.VARIABLES_TABLE_HEADER;
        } else if (modelType == ModelType.TEST_CASE_TABLE_HEADER) {
            type = RobotTokenType.TEST_CASES_TABLE_HEADER;
        } else if (modelType == ModelType.KEYWORDS_TABLE_HEADER) {
            type = RobotTokenType.KEYWORDS_TABLE_HEADER;
        }

        return type;
    }

    public VersionAvailabilityInfo getTheMostCorrectOneRepresentation(final IRobotTokenType type,
            final RobotVersion robotVersionInstalled) {
        VersionAvailabilityInfo vaiInCaseNoMatches = null;
        for (final VersionAvailabilityInfo vai : type.getVersionAvailabilityInfos()) {
            if (vai.getRepresentation() == null) {
                continue;
            }
            if ((vai.getAvailableFrom() == null || robotVersionInstalled.isNewerOrEqualTo(vai.getAvailableFrom()))
                    && vai.getDepracatedFrom() == null && vai.getRemovedFrom() == null) {
                vaiInCaseNoMatches = vai;
                break;
            } else {
                if (vaiInCaseNoMatches == null) {
                    vaiInCaseNoMatches = vai;
                    continue;
                }

                if (vai.getAvailableFrom() == null || robotVersionInstalled.isNewerOrEqualTo(vai.getAvailableFrom())) {
                    if (vai.getRemovedFrom() == null) {
                        if (vaiInCaseNoMatches.getDepracatedFrom() != null
                                && vai.getDepracatedFrom().isNewerThan(vaiInCaseNoMatches.getDepracatedFrom())) {
                            vaiInCaseNoMatches = vai;
                        }
                    } else {
                        if (vaiInCaseNoMatches.getRemovedFrom() != null
                                && vai.getRemovedFrom().isNewerThan(vaiInCaseNoMatches.getRemovedFrom())) {
                            vaiInCaseNoMatches = vai;
                        }
                    }
                }
            }
        }

        return vaiInCaseNoMatches;
    }

    public void dumpSeparatorsAfterToken(final RobotFile model, final RobotLine currentLine,
            final IRobotLineElement currentToken, final List<RobotLine> lines) {
        int dumpEndIndex = -1;
        final List<IRobotLineElement> lineElements = currentLine.getLineElements();
        final int tokenPosIndex = lineElements.indexOf(currentToken);
        for (int index = tokenPosIndex + 1; index < lineElements.size(); index++) {
            if (lineElements.get(index) instanceof RobotToken) {
                break;
            } else {
                dumpEndIndex = index;
            }
        }

        if (dumpEndIndex >= 0) {
            for (int myIndex = tokenPosIndex + 1; myIndex < lineElements.size() && myIndex <= dumpEndIndex; myIndex++) {
                updateLine(model, lines, lineElements.get(myIndex));
            }
        }
    }

    public void dumpSeparatorsBeforeToken(final RobotFile model, final RobotLine currentLine,
            final IRobotLineElement currentToken, final List<RobotLine> lines) {
        int dumpStartIndex = -1;
        final List<IRobotLineElement> lineElements = currentLine.getLineElements();
        final int tokenPosIndex = lineElements.indexOf(currentToken);
        for (int index = tokenPosIndex - 1; index >= 0; index--) {
            if (lineElements.get(index) instanceof RobotToken) {
                break;
            } else {
                dumpStartIndex = index;
            }
        }

        if (dumpStartIndex >= 0) {
            for (int myIndex = dumpStartIndex; myIndex < tokenPosIndex; myIndex++) {
                updateLine(model, lines, lineElements.get(myIndex));
            }
        }
    }

    public IRobotLineElement getLineSeparator(final RobotFile model) {
        String eol = model.getParent().getFileLineSeparator();
        if (eol == null || eol.isEmpty()) {
            eol = System.lineSeparator();
        }

        RobotToken tempEOL = new RobotToken();
        tempEOL.setRaw(eol);
        tempEOL.setText(eol);

        return EndOfLineBuilder.newInstance().setEndOfLines(Constant.get(tempEOL)).buildEOL();
    }

    public int getLastSortedToDump(final RobotFile model, final List<Section> sections,
            final List<AModelElement<ARobotSectionTable>> sortedElements) {
        final int size = sortedElements.size();
        int index = size - 1;
        int nextFound = 0;
        int nextStartFoundIndex = -1;

        if (sections.size() >= 1) {
            final Section currentSection = sections.get(0);
            final Set<Integer> startPosForElements = new HashSet<>();
            final List<Section> subElements = currentSection.getSubSections();
            for (final Section elem : subElements) {
                startPosForElements.add(elem.getStart().getOffset());
            }

            final Set<Integer> nextStartPosForElements = new HashSet<>();
            if (sections.size() > 1) {
                final Section nextSection = sections.get(1);
                final List<Section> nextElements = nextSection.getSubSections();
                for (final Section elem : nextElements) {
                    nextStartPosForElements.add(elem.getStart().getOffset());
                }
            }

            for (int elemIndex = 0; elemIndex < size; elemIndex++) {
                final AModelElement<ARobotSectionTable> e = sortedElements.get(elemIndex);
                FilePosition pos = e.getBeginPosition();
                if (pos.isNotSet()) {
                    if (size == index || elemIndex - 1 == index) {
                        index = elemIndex;
                        nextFound = 0;
                        nextStartFoundIndex = -1;
                    }

                } else if (startPosForElements.contains(pos.getOffset())
                        || containsOneOfElementOffset(e, startPosForElements)) {
                    index = elemIndex;
                    nextFound = 0;
                    nextStartFoundIndex = -1;
                } else {
                    if (nextStartPosForElements.contains(pos.getOffset())
                            || containsOneOfElementOffset(e, nextStartPosForElements)) {
                        if (nextStartFoundIndex == -1) {
                            nextStartFoundIndex = elemIndex;
                            nextFound++;
                        } else if (nextFound == getNumberOfNewToTreatAsNewUnit()) {
                            index = nextStartFoundIndex;
                            break;
                        }
                    } else {
                        Optional<Integer> line = model.getRobotLineIndexBy(pos.getOffset());
                        if (line.isPresent()) {
                            final int lineIndex = line.get();
                            final RobotLine robotLine = model.getFileContent().get(lineIndex);
                            final Optional<Integer> elementPositionInLine = robotLine
                                    .getElementPositionInLine(e.getDeclaration());
                            boolean wasSeparatorsOnly = false;
                            if (elementPositionInLine.isPresent()) {
                                for (int i = elementPositionInLine.get() - 1; i >= 0; i--) {
                                    if (robotLine.getLineElements().get(i) instanceof Separator) {
                                        wasSeparatorsOnly = true;
                                    } else {
                                        wasSeparatorsOnly = false;
                                        break;
                                    }
                                }

                                if (wasSeparatorsOnly) {
                                    index = elemIndex;
                                }
                            }
                        }
                        nextFound = 0;
                        nextStartFoundIndex = -1;
                    }
                }
            }
        }

        return index;
    }

    public <T extends ARobotSectionTable> boolean containsOneOfElementOffset(final AModelElement<T> e,
            final Set<Integer> startPositions) {
        boolean result = false;
        final List<RobotToken> elementTokens = e.getElementTokens();
        for (final RobotToken rt : elementTokens) {
            if (startPositions.contains(rt.getFilePosition().getOffset())) {
                result = true;
                break;
            }
        }

        return result;
    }

    public int getNumberOfNewToTreatAsNewUnit() {
        return NUMBER_OF_AFTER_UNIT_ELEMENTS_TO_TREAT_AS_NEW_UNIT_START;
    }

    public String getEmpty() {
        return EMPTY;
    }
}