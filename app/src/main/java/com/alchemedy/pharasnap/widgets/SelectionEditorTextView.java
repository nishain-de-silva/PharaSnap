package com.alchemedy.pharasnap.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.text.Layout;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.Coordinate;
import com.alchemedy.pharasnap.helper.CoordinateF;

import java.util.ArrayList;

public class SelectionEditorTextView extends androidx.appcompat.widget.AppCompatTextView {
    CursorPoint startCursor, endCursor;
    CoordinateF debugPoint;
    int cursorColor, selectedTextColor;
    Paint paint = new Paint();
    private Path selectedTextPath;
    private float lineSpacingExtra;
    private int cursorWidth;

    //    ArrayList<Rect> lineBounds = new ArrayList<>();
    public SelectionEditorTextView(Context context) {
        super(context);
        init();
    }

    public SelectionEditorTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SelectionEditorTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    Layout layout;
    Pair<Integer, Integer>[] wordSnapLocations;
    Coordinate paddingInset;
    enum CursorSelectionMode {
        NOT_SELECTED,
        SELECTED_START,
        SELECTED_END
    }

    class CursorPoint {
        CoordinateF cursorCoordinate;
        CoordinateF downCursorCoordinate;
        int lineIndex;
        @Nullable Pair<Integer, Integer> associatedWordSpan;
        int cursorIndex;

        float lineHeight;
        Rect cursorThumbRect;

        CursorPoint(int offset, int lineIndex) {
            updateInternal(offset, lineIndex, true);
        }

        void update(int offset, int lineIndex) {
            updateInternal(offset, lineIndex, true);
        }
        private void updateInternal(int offset, int lineIndex, boolean shouldRescanAssociatedWord) {
            float positionX = layout.getPrimaryHorizontal(offset);
            associatedWordSpan = null;
            if (shouldRescanAssociatedWord) {
                for (int i = 0; i < wordSnapLocations.length; i++) {
                    if (offset < wordSnapLocations[i].first)
                        break;
                    if (offset <= wordSnapLocations[i].second) {
                        associatedWordSpan = wordSnapLocations[i];
                        break;
                    }
                }
            }
            Rect bounds = new Rect();
            layout.getLineBounds(lineIndex, bounds);
            bounds.offset(paddingInset.x, paddingInset.y);
            int lineTop = bounds.top;
            lineHeight =  bounds.bottom - lineTop - (lineIndex + 1 < layout.getLineCount() ? lineSpacingExtra : 0);
            float lineBottom = lineTop + lineHeight;
            cursorIndex = offset;
            cursorCoordinate = new CoordinateF(positionX + paddingInset.x, lineTop + (lineHeight / 2f) + paddingInset.y);
            this.lineIndex = lineIndex;
            int thumbSize = getContext().getResources().getDimensionPixelSize(R.dimen.thumb_size);
            cursorThumbRect = new Rect(
                    (int) (cursorCoordinate.x - thumbSize / 2),
                    (int) (lineBottom),
                    (int) (cursorCoordinate.x + thumbSize / 2),
                    (int) lineBottom + thumbSize
            );
        }

        void snapToIndex(int index) {
            updateInternal(index, lineIndex, false);
        }

        void recordDownCoordinate() {
            downCursorCoordinate = cursorCoordinate;
        }
    }

    void drawTextSelectionPolygon() {
        float curvature = lineSpacingExtra;
        int startLineIndex = startCursor.associatedWordSpan == null ? startCursor.lineIndex : layout.getLineForOffset(startCursor.associatedWordSpan.first);
        int endLineIndex = endCursor.associatedWordSpan == null ? endCursor.lineIndex : layout.getLineForOffset(endCursor.associatedWordSpan.second);
        Rect[] lineBounds = new Rect[endLineIndex + 1 - startLineIndex];
        selectedTextPath = new Path();

        for (int i = 0; i < lineBounds.length; i++) {
            Rect bounds = new Rect();

            layout.getLineBounds(i + startLineIndex, bounds);
            bounds.offset(paddingInset.x, paddingInset.y);
            if (i == 0) {
                if (startCursor.associatedWordSpan != null)
                    bounds.left = (int) (layout.getPrimaryHorizontal(startCursor.associatedWordSpan.first) + paddingInset.x);
                else
                    bounds.left = (int) startCursor.cursorCoordinate.x;
                bounds.left += curvature;
                bounds.right = (int) layout.getLineRight(i + startLineIndex) + paddingInset.x;
            }


            if (i == lineBounds.length - 1) {
                if (endCursor.associatedWordSpan != null)
                    bounds.right = (int) (layout.getPrimaryHorizontal(endCursor.associatedWordSpan.second) + paddingInset.x);
                else
                    bounds.right = (int) endCursor.cursorCoordinate.x;
                bounds.right -= curvature;
            } else {
                bounds.right = (int) layout.getLineRight(i + startLineIndex) + paddingInset.x;
            }

            lineBounds[i] = bounds;
            bounds.left -= curvature;
            bounds.top -= curvature;
            bounds.right += curvature;

            int previousIndex = i - 1;
            if (previousIndex > -1) {
                if (lineBounds[previousIndex].right != bounds.right) {
                    if (bounds.right > lineBounds[previousIndex].right) {
                        selectedTextPath.lineTo(lineBounds[previousIndex].right,bounds.top);
                        selectedTextPath.lineTo(bounds.right - curvature, bounds.top);
                        selectedTextPath.quadTo(
                                bounds.right, bounds.top,
                                bounds.right, bounds.top + curvature
                        );
                        selectedTextPath.lineTo(bounds.right, bounds.bottom - curvature);
                    } else {
                        selectedTextPath.quadTo(
                                lineBounds[previousIndex].right, lineBounds[previousIndex].bottom,
                                lineBounds[previousIndex].right -curvature, lineBounds[previousIndex].bottom
                        );
                        selectedTextPath.lineTo(bounds.right, lineBounds[previousIndex].bottom);
                        selectedTextPath.lineTo(bounds.right, bounds.bottom - curvature);
                    }
                } else {
                    selectedTextPath.lineTo(bounds.right, bounds.bottom - curvature);
                }


            } else {
                // top-left section
                selectedTextPath.moveTo(bounds.left, bounds.top + curvature);
                selectedTextPath.quadTo(
                        bounds.left, bounds.top,
                        bounds.left + curvature, bounds.top
                );
                selectedTextPath.lineTo(bounds.right - curvature, bounds.top);
                selectedTextPath.quadTo(
                        bounds.right, bounds.top,
                        bounds.right, bounds.top + curvature
                );
                selectedTextPath.lineTo(bounds.right, bounds.bottom - curvature);
            }

            if (i == lineBounds.length - 1) {
                // last bottom-right section
                selectedTextPath.quadTo(
                        bounds.right, bounds.bottom,
                        bounds.right - curvature, bounds.bottom
                );
                selectedTextPath.lineTo(bounds.left + curvature, bounds.bottom);
                selectedTextPath.quadTo(
                        bounds.left, bounds.bottom,
                        bounds.left, bounds.bottom - curvature
                );
                if (bounds.left != lineBounds[0].left) {
                    selectedTextPath.lineTo(bounds.left, lineBounds[1].top + curvature);
                    selectedTextPath.quadTo(
                            bounds.left, lineBounds[1].top,
                            bounds.left + curvature, lineBounds[1].top
                    );
                    selectedTextPath.lineTo(lineBounds[0].left - curvature, lineBounds[1].top);
                    selectedTextPath.quadTo(
                            lineBounds[0].left, lineBounds[1].top,
                            lineBounds[0].left, lineBounds[1].top - curvature
                    );
                }
                selectedTextPath.close();
            }
        }
        invalidate();
    }
    void init() {
        cursorColor = ContextCompat.getColor(getContext(), R.color.darkPurple);
        selectedTextColor = ContextCompat.getColor(getContext(), R.color.textSelection);
        cursorWidth = getResources().getDimensionPixelSize(R.dimen.cursor_width);
    }

    public void changeText(CharSequence text) {
        post(this::selectAllText);
        super.setText(text);
    }

    public void selectAllText() {
        layout = getLayout();
        if (layout == null) {
            return;
        }
        lineSpacingExtra = getLineSpacingExtra();

        paddingInset = new Coordinate(getPaddingLeft(), getPaddingTop());
        CharSequence originalText = getText();

        // generating cursor snapping index list
        ArrayList<Pair<Integer, Integer>> wordEndLocations = new ArrayList<>();
        int startIndex = 0;
        /* previousCharacterCaseType modes:
            0 - lowercase letter
            1 - uppercase letter
            2 - number
            3 - other (not alphanumeric)
           */
        int previousCharacterCaseType = 3;
        final int terminateIndex = originalText.length() - 1;
        for (int i = 0; i < originalText.length(); i++) {
            char character = originalText.charAt(i);
            int characterCaseType;
            if (Character.isAlphabetic(character)) {
                characterCaseType = Character.isUpperCase(character) ? 1 : 0;
            } else if (Character.isDigit(character)) {
                characterCaseType = 2;
            } else
                characterCaseType = 3;
            if(previousCharacterCaseType == 3 && characterCaseType < 3)
                // if previous character is NOT alphanumeric and this character is
                // alphanumeric indicating start of an word
                startIndex = i;
            else if (previousCharacterCaseType < 3) {
                // if previous character is alphanumeric and this character is not
                // Eg hello#word where # captures the 'hello'.
                if (characterCaseType == 3)
                    wordEndLocations.add(new Pair<>(startIndex, i));
                else {
                    if (i == terminateIndex)
                        wordEndLocations.add(new Pair<>(startIndex, terminateIndex + 1));
                    else if (
                            // At reaching this point both previousCharacterCaseType and characterCaseType
                            // are alpha numeric here breaks down combined words such as HelloWorld or hello25
                            (previousCharacterCaseType == 0 && characterCaseType == 1)
                            || (previousCharacterCaseType == 2 && characterCaseType != 2)
                                    || (previousCharacterCaseType != 2 && characterCaseType == 2)
                    ) {
                        wordEndLocations.add(new Pair<>(startIndex, i));
                        startIndex = i;
                    }
                }
            }
            previousCharacterCaseType = characterCaseType;
        }
        wordSnapLocations = wordEndLocations.toArray(new Pair[0]);

        int lastIndex = originalText.length();
        int lastIndexLine = layout.getLineForOffset(lastIndex);
        endCursor = new CursorPoint(lastIndex, lastIndexLine);
        startCursor = new CursorPoint(0, 0);
        drawTextSelectionPolygon();

        setOnTouchListener(new OnTouchListener() {
            CursorSelectionMode selectedCursor = CursorSelectionMode.NOT_SELECTED;
            CoordinateF downCoordinate = new CoordinateF(0, 0);
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                int action = motionEvent.getAction();
                int eventX = (int) motionEvent.getX();
                int eventY = (int) motionEvent.getY();

                if (action == MotionEvent.ACTION_MOVE) {
                    if (selectedCursor == CursorSelectionMode.NOT_SELECTED) {
                        return true;
                    }

                    if (selectedCursor == CursorSelectionMode.SELECTED_START) {
                        int cursorMovementX = (int) (startCursor.downCursorCoordinate.x + eventX - downCoordinate.x - paddingInset.x);
                        int cursorMovementY = (int) (startCursor.downCursorCoordinate.y + eventY - downCoordinate.y - paddingInset.y);
                        int line = layout.getLineForVertical(cursorMovementY);
                        int offset = layout.getOffsetForHorizontal(line, cursorMovementX);
                        if (offset == startCursor.cursorIndex) return true;
                        startCursor.update(offset, line);
                        if (offset >= endCursor.cursorIndex) {
                            selectedCursor = CursorSelectionMode.SELECTED_END;
                            CursorPoint reference = endCursor;
                            endCursor = startCursor;
                            startCursor = reference;
                        }
                        drawTextSelectionPolygon();
                    } else if (selectedCursor == CursorSelectionMode.SELECTED_END) {
                        int cursorMovementX = (int) (endCursor.downCursorCoordinate.x + eventX - downCoordinate.x - paddingInset.x);
                        int cursorMovementY = (int) (endCursor.downCursorCoordinate.y + eventY - downCoordinate.y - paddingInset.y);

                        int line = layout.getLineForVertical(cursorMovementY);
                        int offset = layout.getOffsetForHorizontal(line, cursorMovementX);
                        if (offset == endCursor.cursorIndex) return true;
                        endCursor.update(offset, line);
                        if(offset <= startCursor.cursorIndex) {
                            selectedCursor = CursorSelectionMode.SELECTED_START;
                            CursorPoint reference = startCursor;
                            startCursor = endCursor;
                            endCursor = reference;
                        }
                        drawTextSelectionPolygon();
                    }
                } else if (action == MotionEvent.ACTION_UP) {
                    if(selectedCursor == CursorSelectionMode.NOT_SELECTED &&
                            downCoordinate.isCloserTo(eventX, eventY, 15)) {
                        int cursorMovementX = eventX - paddingInset.x;
                        int cursorMovementY = eventY - paddingInset.y;
                        int line = layout.getLineForVertical(cursorMovementY);
                        int offset = layout.getOffsetForHorizontal(line, cursorMovementX);
                        CharSequence text = getText();
                        int textLength = text.length();
                        int startIndex = 0;
                        int endIndex = textLength;
                        for (int i = 0; i < textLength - 1; i++) {
                            char character = text.charAt(i);
                            if (Character.isWhitespace(character) || character == '.' || character == ',') {
                                if (i == offset) return true;
                                if (i > offset) {
                                    endIndex = i;
                                    break;
                                }
                                startIndex = i + 1;
                            }
                        }
                        startCursor.update(startIndex, layout.getLineForOffset(startIndex));
                        endCursor.update(endIndex, layout.getLineForOffset(endIndex));
                        drawTextSelectionPolygon();
                    } else if (selectedCursor != CursorSelectionMode.NOT_SELECTED) {
                        if (selectedCursor == CursorSelectionMode.SELECTED_START && startCursor.associatedWordSpan != null) {
                            startCursor.snapToIndex(startCursor.associatedWordSpan.first);
                            drawTextSelectionPolygon();
                        } else if (selectedCursor == CursorSelectionMode.SELECTED_END && endCursor.associatedWordSpan != null) {
                            endCursor.snapToIndex(endCursor.associatedWordSpan.second);
                            drawTextSelectionPolygon();
                        }
                    }
                    getParent().requestDisallowInterceptTouchEvent(false);
                    selectedCursor = CursorSelectionMode.NOT_SELECTED;
                } else if (action == MotionEvent.ACTION_DOWN) {
                    downCoordinate = new CoordinateF(eventX, eventY);

                    if (startCursor.cursorThumbRect.contains(eventX, eventY)) {
                        selectedCursor = CursorSelectionMode.SELECTED_START;
                        startCursor.recordDownCoordinate();
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    else if (endCursor.cursorThumbRect.contains(eventX, eventY)) {
                        selectedCursor = CursorSelectionMode.SELECTED_END;
                        endCursor.recordDownCoordinate();
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                return true;
            }
        });
    }

    public String getSelectedText() {
        return getText().toString().substring(startCursor.cursorIndex, endCursor.cursorIndex);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (selectedTextPath != null) {
            paint.setColor(selectedTextColor);
            canvas.drawPath(selectedTextPath, paint);
        }
        super.draw(canvas);
        if (startCursor != null && endCursor != null) {
            paint.setColor(cursorColor);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(cursorWidth);
            canvas.drawLine(startCursor.cursorCoordinate.x, startCursor.cursorThumbRect.top - startCursor.lineHeight, startCursor.cursorCoordinate.x, startCursor.cursorThumbRect.top, paint);
            canvas.drawLine(endCursor.cursorCoordinate.x, endCursor.cursorThumbRect.top - endCursor.lineHeight, endCursor.cursorCoordinate.x, endCursor.cursorThumbRect.top, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(startCursor.cursorThumbRect.exactCenterX(), startCursor.cursorThumbRect.exactCenterY(), startCursor.cursorThumbRect.height() / 2f, paint);
            canvas.drawCircle(endCursor.cursorThumbRect.exactCenterX(), endCursor.cursorThumbRect.exactCenterY(), endCursor.cursorThumbRect.height() / 2f, paint);
        }
        if (debugPoint != null) {
            paint.setColor(Color.RED);
            canvas.drawCircle(debugPoint.x, debugPoint.y, 8f, paint);
        }
    }
}
