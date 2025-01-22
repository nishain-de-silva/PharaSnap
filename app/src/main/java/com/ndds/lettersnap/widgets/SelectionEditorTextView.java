package com.ndds.lettersnap.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.text.Layout;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ndds.lettersnap.R;
import com.ndds.lettersnap.helper.Coordinate;
import com.ndds.lettersnap.helper.CoordinateF;

public class SelectionEditorTextView extends androidx.appcompat.widget.AppCompatTextView {
    CursorPoint startCursor, endCursor;
    CoordinateF debugPoint;
    int primaryColor, transparentPrimaryColor;
    Paint paint = new Paint();
    private Path selectedTextPath;

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
        int cursorIndex;

        int lineHeight;
        Rect cursorThumbRect;

        CursorPoint(int offset, int lineIndex) {
            update(offset, lineIndex);
        }

        void update(int offset, int lineIndex) {
            float positionX = layout.getPrimaryHorizontal(offset);
            Rect bounds = new Rect();
            layout.getLineBounds(lineIndex, bounds);
            bounds.offset(paddingInset.x, paddingInset.y);
            int lineTop = bounds.top;
            int lineBottom = bounds.bottom;
            lineHeight = lineBottom - lineTop;
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

        void recordDownCoordinate() {
            downCursorCoordinate = cursorCoordinate;
        }
    }

    void calculateLineBounds() {
        int curvature = 10;
        Rect[] lineBounds = new Rect[endCursor.lineIndex + 1 - startCursor.lineIndex];
        selectedTextPath = new Path();

        for (int i = 0; i < lineBounds.length; i++) {
            Rect bounds = new Rect();

            layout.getLineBounds(i + startCursor.lineIndex, bounds);
            bounds.offset(paddingInset.x, paddingInset.y);
            if (i == 0) {
                bounds.left = (int) startCursor.cursorCoordinate.x;
                bounds.left += curvature;
                bounds.right = (int) layout.getLineRight(i + startCursor.lineIndex) + paddingInset.x;
            }


            if (i == lineBounds.length - 1) {
                bounds.right = (int) endCursor.cursorCoordinate.x;
                bounds.right -= curvature;
            } else {
                bounds.right = (int) layout.getLineRight(i + startCursor.lineIndex) + paddingInset.x;
            }

            lineBounds[i] = bounds;
            bounds.left -= curvature;
            bounds.top -= curvature;
            bounds.right += curvature;
//            bounds.bottom += curvature;

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
        primaryColor = ContextCompat.getColor(getContext(), R.color.primary);
        transparentPrimaryColor = ContextCompat.getColor(getContext(), R.color.primaryTransparent);
    }

    public void selectAllText() {
        layout = getLayout();
        if (layout == null) {
            return;
        };

        paddingInset = new Coordinate(getPaddingLeft(), getPaddingTop());
        int lastIndex = getText().length();
        int lastIndexLine = layout.getLineForOffset(lastIndex);
        endCursor = new CursorPoint(lastIndex, lastIndexLine);
        startCursor = new CursorPoint(0, 0);
        calculateLineBounds();

        setOnTouchListener(new OnTouchListener() {
            CursorSelectionMode selectedCursor = CursorSelectionMode.NOT_SELECTED;
            CoordinateF tappedCoordinate = new CoordinateF(0, 0);
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                int action = motionEvent.getAction();

                int relativeX = (int) motionEvent.getX();
                int relativeY = (int) motionEvent.getY();
                if (action == MotionEvent.ACTION_MOVE) {
                    if (selectedCursor == CursorSelectionMode.NOT_SELECTED) {
                        return true;
                    }

                    if (selectedCursor == CursorSelectionMode.SELECTED_START) {
                        int cursorMovementX = (int) (startCursor.downCursorCoordinate.x + relativeX - tappedCoordinate.x - paddingInset.x);
                        int cursorMovementY = (int) (startCursor.downCursorCoordinate.y + relativeY - tappedCoordinate.y - paddingInset.y);
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
                        calculateLineBounds();
                    } else if (selectedCursor == CursorSelectionMode.SELECTED_END) {
                        int cursorMovementX = (int) (endCursor.downCursorCoordinate.x + relativeX - tappedCoordinate.x - paddingInset.x);
                        int cursorMovementY = (int) (endCursor.downCursorCoordinate.y + relativeY - tappedCoordinate.y - paddingInset.y);
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
                        calculateLineBounds();
                    }
                } else if (action == MotionEvent.ACTION_UP) {
                    if(selectedCursor == CursorSelectionMode.NOT_SELECTED &&
                            tappedCoordinate.isCloserTo(relativeX, relativeY, 15)) {
                        int cursorMovementX = relativeX - paddingInset.x;
                        int cursorMovementY = relativeY - paddingInset.y;
                        int line = layout.getLineForVertical(cursorMovementY);
                        int offset = layout.getOffsetForHorizontal(line, cursorMovementX);
                        CharSequence text = getText();
                        int textLength = text.length();
                        int startIndex = 0;
                        int endIndex = textLength;
                        for (int i = 0; i < textLength - 1; i++) {
                            char character = text.charAt(i);
                            if (Character.isWhitespace(character)|| character == '.' || character == ',') {
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
                        calculateLineBounds();
                    }
                    getParent().requestDisallowInterceptTouchEvent(false);
                    selectedCursor = CursorSelectionMode.NOT_SELECTED;
                } else if (action == MotionEvent.ACTION_DOWN) {
                    tappedCoordinate = new CoordinateF(relativeX, relativeY);

                    if (startCursor.cursorThumbRect.contains(relativeX, relativeY)) {
                        selectedCursor = CursorSelectionMode.SELECTED_START;
                        startCursor.recordDownCoordinate();
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    else if (endCursor.cursorThumbRect.contains(relativeX, relativeY)) {
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
            paint.setColor(transparentPrimaryColor);
            canvas.drawPath(selectedTextPath, paint);
        }
        super.draw(canvas);
        if (startCursor != null && endCursor != null) {
            paint.setColor(primaryColor);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
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
