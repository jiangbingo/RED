/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.rf.ide.core.testdata.model.table.variables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.rf.ide.core.testdata.model.presenter.MoveElementHelper;
import org.rf.ide.core.testdata.text.read.recognizer.RobotToken;
import org.rf.ide.core.testdata.text.read.recognizer.RobotTokenType;

public class UnknownVariable extends AVariable {

    private static final long serialVersionUID = -5596982418926479458L;

    private final List<RobotToken> items = new ArrayList<>();

    public UnknownVariable(final String name, final RobotToken declaration, final VariableScope scope) {
        super(VariableType.INVALID, name, declaration, scope);
    }

    public void addItem(final RobotToken item) {
        fixForTheType(item, RobotTokenType.VARIABLES_VARIABLE_VALUE);
        items.add(item);
    }

    public void addNewItem(final int position, final RobotToken item) {
        fixForTheType(item, RobotTokenType.VARIABLES_VARIABLE_VALUE);
        items.add(position, item);
    }

    public void addItem(final RobotToken item, final int position) {
        updateOrCreateTokenInside(items, position, item, RobotTokenType.VARIABLES_VARIABLE_VALUE);
    }

    public void removeItem(final RobotToken item) {
        items.remove(item);
    }

    public boolean moveLeftItem(final RobotToken item) {
        return MoveElementHelper.moveLeft(items, item);
    }

    public boolean moveRightItem(final RobotToken item) {
        return MoveElementHelper.moveRight(items, item);
    }

    public List<RobotToken> getItems() {
        return Collections.unmodifiableList(items);
    }

    @Override
    public List<RobotToken> getValueTokens() {
        return getItems();
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    public List<RobotToken> getElementTokens() {
        final List<RobotToken> tokens = new ArrayList<>();
        if (isPresent()) {
            if (getDeclaration() != null) {
                tokens.add(getDeclaration());
            }

            tokens.addAll(getItems());
            tokens.addAll(getComment());
        }

        return tokens;
    }

    @Override
    public boolean removeElementToken(final int index) {
        return super.removeElementFromList(items, index);
    }
}
