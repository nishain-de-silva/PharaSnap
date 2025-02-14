package com.ndds.lettersnap.services;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.ndds.lettersnap.R;
import com.ndds.lettersnap.activities.MediaProjectionRequestActivity;
import com.ndds.lettersnap.helper.Constants;
import com.ndds.lettersnap.helper.CoordinateF;
import com.ndds.lettersnap.helper.OnTapListener;
import com.ndds.lettersnap.helper.ScreenInfo;
import com.ndds.lettersnap.utils.EntryListAdapter;
import com.ndds.lettersnap.utils.Modal;
import com.ndds.lettersnap.utils.WidgetController;
import com.ndds.lettersnap.widgets.CustomOverlayView;
import com.ndds.lettersnap.widgets.EnableButton;
import com.ndds.lettersnap.widgets.SelectionEditorTextView;
import com.ndds.lettersnap.widgets.TextHint;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class NodeExplorerAccessibilityService extends android.accessibilityservice.AccessibilityService {
    private WindowManager windowManager;
    private View overlayView;
    private WidgetController widgetController;
    private boolean isWidgetExpanded = false;
    private boolean skipNotifyOnWidgetStop = false;
    private Modal modal;
    private BroadcastReceiver systemNavigationButtonTapListener = null;
    private boolean isTextRecognitionEnabled = false;
    private MediaProjection mediaProjection;
    private MediaProjectionManager mediaProjectionManager;
    private Handler mediaProjectionIdleHandler = null;
    private Runnable mediaProjectionIdlePostRunnable = null;
    private ImageReader imageReader = null;
    private ClipboardManager clipboardManager;
    private ImageButton toggleModeButton;
    private TextHint textHint;
    private VirtualDisplay mediaProjectionDisplay;
    private boolean isAccessibilityServiceBusy;

    private CustomOverlayView rootOverlay;

    private int currentOrientation;
    private SharedPreferences sharedPreferences;

    private BroadcastReceiver tileMessageReceiver;
    private AccessibilityNodeInfo selectedNode;
    private int minArea;


    private ScreenInfo getScreenConfiguration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect screenBounds = windowManager.getMaximumWindowMetrics().getBounds();
            return new ScreenInfo(screenBounds.width(), screenBounds.height(), getResources().getConfiguration().screenHeightDp);
        } else {
            DisplayMetrics realDisplayMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(realDisplayMetrics);
            return new ScreenInfo(realDisplayMetrics.widthPixels, realDisplayMetrics.heightPixels, realDisplayMetrics.densityDpi);
        }
    }

    private void showResult(Rect bound, String text) {
        TextHint label = overlayView.findViewById(R.id.text_hint);

        if (text.isEmpty()) {
            rootOverlay.hideBoundingBox();
            label.changeText(getString(R.string.text_detection_failed));
            return;
        }
        rootOverlay.showBoundingBox(bound);

        label.onTextCaptured(new TextHint.OnTapListener() {
            @Override
            public void onTap() {
                setupEditSelectionModal(text, true);
            }
        });
        copyTextToClipboard(text);
        saveTextToSharedPreferences(text);
    }

    void saveTextToSharedPreferences(String text) {
        ArrayList<String> entries = new ArrayList<>();
        try {
            JSONArray inputJSONArray = new JSONArray(sharedPreferences.getString(Constants.ENTRIES_KEY, "[]"));
            for (int i = 0; i < inputJSONArray.length(); i++) {
                entries.add(inputJSONArray.getString(i));
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        entries.add(0, text);
        while (entries.size() > 20)
            entries.remove(entries.size() - 1);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Constants.ENTRIES_KEY, new JSONArray(entries).toString());
        editor.apply();
    }

    void copyTextToClipboard(String text) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("copied text", text));
    }

    void setupEditSelectionModal(String text, boolean shouldSaveCopiedText) {
        modal.showModal(Modal.ModalType.EDIT_SELECTION, new Modal.ModalCallback() {
            SelectionEditorTextView selectedText;

            @Override
            public void onOpened(ViewGroup inflatedView, boolean isModalAlreadyOpened) {
                if (isModalAlreadyOpened)
                    new Handler().postDelayed(() -> selectedText.selectAllText(), 150);
                else
                    selectedText.selectAllText();
            }

            @Override
            public void onHeaderBackPressed(ViewGroup modalWindow) {
                if (shouldSaveCopiedText)
                    super.onHeaderBackPressed(modalWindow);
                else
                    showEntryList();
            }

            @Override
            public void onBeforeModalShown(ViewGroup inflatedView) {
                selectedText = inflatedView.findViewById(R.id.selectedText);
                selectedText.setText(text);
                inflatedView.findViewById(R.id.action_copy_entire_text).setOnClickListener(v -> {
                    selectedText.selectAllText();
                });
                inflatedView.findViewById(R.id.action_copy_selected_text).setOnClickListener(v -> {
                    String text = selectedText.getSelectedText();
                    copyTextAndDismiss(text);
                });
            }
        });
    }

    void showEntryList() {
        modal.showModal(Modal.ModalType.ENTRY_LIST, new Modal.ModalCallback() {
            @Override
            public void onBeforeModalShown(ViewGroup inflatedView) {
                String jsonString = sharedPreferences.getString(Constants.ENTRIES_KEY, "[]");
                ArrayList<String> items = new ArrayList<>();
                try {
                    JSONArray jsonArray = new JSONArray(jsonString);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        items.add(jsonArray.getString(i));
                    }

                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                EntryListAdapter adapter = new EntryListAdapter(NodeExplorerAccessibilityService.this, items, new EntryListAdapter.OnTapListener() {
                    @Override
                    public void onTap(String text) {
                        setupEditSelectionModal(text, false);
                    }

                    @Override
                    public void onTapCopyToClipboardShortcut(String text) {
                        copyTextAndDismiss(text);
                    }
                });
                inflatedView.findViewById(R.id.list_clear).setOnClickListener(v -> {
                    adapter.clear();
                    sharedPreferences.edit().remove(Constants.ENTRIES_KEY).apply();
                });
                ListView listView = inflatedView.findViewById(R.id.entry_list);
                listView.setEmptyView(
                        inflatedView.findViewById(R.id.list_empty)
                );
                listView.setAdapter(adapter);
            }
        });
    }

    void copyTextAndDismiss(String text) {
        copyTextToClipboard(text);
        toggleExpandWidget();
    }

    void startCountdownToTerminateMediaProjection() {
        if (mediaProjection!= null && mediaProjectionIdleHandler == null) {
            mediaProjectionIdlePostRunnable = () -> {
                // completely shutdown if idle for some time
                mediaProjectionIdlePostRunnable = null;
                mediaProjectionIdleHandler = null;
                mediaProjection.stop();
            };
            mediaProjectionIdleHandler = new Handler();
            mediaProjectionIdleHandler.postDelayed(mediaProjectionIdlePostRunnable, 1000 * 5);
        }
    }
    private void toggleTextRecognitionMode(boolean skipCountdownTermination) {
        toggleModeButton.setImageResource(isTextRecognitionEnabled ? R.drawable.character : R.drawable.image);
        if (isTextRecognitionEnabled) {
            if (!skipCountdownTermination)
                startCountdownToTerminateMediaProjection();
        } else {
            if (imageReader == null) {
                requestMediaProjection();
            } else {
                if (mediaProjectionIdleHandler != null) {
                    mediaProjectionIdleHandler.removeCallbacks(mediaProjectionIdlePostRunnable);
                    mediaProjectionIdlePostRunnable = null;
                    mediaProjectionIdleHandler = null;
                }
            }
        }
        rootOverlay.hideBoundingBox();
        isTextRecognitionEnabled = !isTextRecognitionEnabled;
        TextHint textHint = overlayView.findViewById(R.id.text_hint);
        textHint.changeMode(isTextRecognitionEnabled);
    }

    private void toggleExpandWidget() {
        TextHint textHint = overlayView.findViewById(R.id.text_hint);
        if (isWidgetExpanded) {
            modal.closeModal();
            rootOverlay.hideBoundingBox();
            widgetController.close();
            if (isTextRecognitionEnabled)
                startCountdownToTerminateMediaProjection();
            unregisterReceiver(systemNavigationButtonTapListener);
            systemNavigationButtonTapListener = null;
        } else {
            if (isTextRecognitionEnabled) {
                if (imageReader == null) {
                    toggleTextRecognitionMode(true);
                } else {
                    if (mediaProjectionIdleHandler != null) {
                        mediaProjectionIdleHandler.removeCallbacks(mediaProjectionIdlePostRunnable);
                        mediaProjectionIdlePostRunnable = null;
                        mediaProjectionIdleHandler = null;
                    }
                }
            }
            systemNavigationButtonTapListener = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
//                    String reason = intent.getStringExtra("reason");
//                    if("homekey".equals(reason) || "recentapps".equals(reason)) {
//
//                    }
                    if(isWidgetExpanded)
                        toggleExpandWidget();
                }
            };
            registerReceiver(systemNavigationButtonTapListener, new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
            widgetController.open();
        }

        textHint.setVisibility(isWidgetExpanded ? View.GONE : View.VISIBLE);
        isWidgetExpanded = !isWidgetExpanded;
    }

    private boolean showFloatingWidget(boolean shouldExpand) {
        if (overlayView != null) {
            Toast.makeText(this, "Widget is already launched", Toast.LENGTH_SHORT).show();
            return false;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        // Inflate overlay layout
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null);
        modal = new Modal(overlayView);

        currentOrientation = getResources().getConfiguration().orientation;
        // Initial WindowManager params (clicks pass through the overlay)
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY: WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER_VERTICAL | Gravity.END;

        ((EnableButton) overlayView.findViewById(R.id.toggleCollapse))
                .configure(new OnTapListener() {
                    @Override
                    public void onTap(CoordinateF tappedCoordinate) {
                        toggleExpandWidget();
                    }
                }, overlayView, windowManager, params);
        // Add the overlay view
        windowManager.addView(overlayView, params);
        widgetController = new WidgetController(this, windowManager, params, overlayView);

        // Find the button in the overlay
        rootOverlay = overlayView.findViewById(R.id.rootOverlay);
        rootOverlay.setLayoutTransition(null);

        toggleModeButton = overlayView.findViewById(R.id.toggle);
        ImageButton listButton = overlayView.findViewById(R.id.list);
        ImageButton stopButton = overlayView.findViewById(R.id.stop);
        textHint = overlayView.findViewById(R.id.text_hint);


        toggleModeButton.setOnClickListener(v -> toggleTextRecognitionMode(false));
        stopButton.setOnClickListener(v -> onStopWidget());
        listButton.setOnClickListener(v -> showEntryList());
        overlayView.findViewById(R.id.text_hint_close).setOnClickListener(v -> textHint.setVisibility(View.GONE));

        widgetController.prepareInitialState();

        // Handle touch events on the overlay
        rootOverlay.setOnTapListener(new OnTapListener() {
            @Override
            public void onTap(CoordinateF tappedCoordinate) {
                // Record tap coordinates
                if (isAccessibilityServiceBusy) {
                    textHint.changeText("Sorry, we are still busy looking for text element you tapped previously");
                    return;
                }
                overlayView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                        overlayView.removeOnLayoutChangeListener(this);
                        getNodeResult(tappedCoordinate.x, tappedCoordinate.y, !isTextRecognitionEnabled, new NodeCapturedCallback() {
                            @Override
                            void onResult(NodeResult result) {
                                isAccessibilityServiceBusy = false;
                                // ...regain focus to overlay view to capture key event on system navigation back press
                                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                                windowManager.updateViewLayout(overlayView, params);

                                if (isTextRecognitionEnabled) {
                                    rootOverlay.hideBoundingBox();
                                    processScreenshot(result.bound, (int) tappedCoordinate.x, (int) tappedCoordinate.y);
                                    return;
                                }
                                showResult(result.bound, result.text);
                            }
                        });
                    }
                });

                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                windowManager.updateViewLayout(overlayView, params);
            }

            @Override
            public void onDrag(float dragDistance) {
                if (dragDistance > 50)
                    toggleExpandWidget();
                else {
                    textHint.changeText(getString(R.string.text_hint_close_overlay));
                    rootOverlay.hideBoundingBox();
                }
            }
        });

        if (shouldExpand) {
            toggleExpandWidget();
        }

        rootOverlay.setOnDismissListener(new CustomOverlayView.OnDismissListener() {
            @Override
            protected void onDismiss() {
                if(isWidgetExpanded && !modal.handleSystemGoBack())
                    toggleExpandWidget();
            }
        });

        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (rootOverlay != null && newConfig.orientation != currentOrientation) {
            currentOrientation = newConfig.orientation;
            rootOverlay.hideBoundingBox();
            if (mediaProjectionDisplay != null) {
                ScreenInfo screenInfo = getScreenConfiguration();
                mediaProjectionDisplay.resize(screenInfo.width, screenInfo.height, screenInfo.densityDpi);
                imageReader.close();
                imageReader = ImageReader.newInstance(screenInfo.width, screenInfo.height, PixelFormat.RGBA_8888, 1);
                mediaProjectionDisplay.setSurface(imageReader.getSurface());
            }
            DisplayMetrics displayMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) overlayView.getLayoutParams();
            params.height = displayMetrics.heightPixels;
            windowManager.updateViewLayout(overlayView, params);
        }
    }

    private void setIsProcessing(boolean isProcessing) {
        overlayView.setClickable(!isProcessing);
        overlayView.findViewById(R.id.buttonContainer).setVisibility(isProcessing ? View.GONE : View.VISIBLE);
        overlayView.findViewById(R.id.loading).setVisibility(isProcessing ? View.VISIBLE : View.GONE);
    }

    void processScreenshot(Rect bounds, int tappedCoordinateX, int tappedCoordinateY) {
        overlayView.findViewById(R.id.buttonContainer).setVisibility(View.GONE);
        TextHint textHint = overlayView.findViewById(R.id.text_hint);
        textHint.setVisibility(View.GONE);
        new Handler().postDelayed(() -> {
            Image image = imageReader.acquireLatestImage();
            overlayView.findViewById(R.id.buttonContainer).setVisibility(View.VISIBLE);
            textHint.setVisibility(View.VISIBLE);
            Image.Plane plane = image.getPlanes()[0];

            Bitmap screenBitmap = Bitmap.createBitmap(plane.getRowStride() / plane.getPixelStride(),
                    image.getHeight(), Bitmap.Config.ARGB_8888);
            screenBitmap.copyPixelsFromBuffer(plane.getBuffer());
            image.close();

            TextRecognizer recognizer =
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            bounds.bottom = Math.min(bounds.bottom, screenBitmap.getHeight());
            bounds.top = Math.max(bounds.top, 0);

            Bitmap croppedImage = Bitmap.createBitmap(screenBitmap, bounds.left, bounds.top, bounds.width(), bounds.height());
//            ImageView debugImage = overlayView.findViewById(R.id.debug_image);
//            debugImage.setVisibility(View.VISIBLE);
//            debugImage.setImageBitmap(croppedImage);
            setIsProcessing(true);
            recognizer.process(
                            InputImage.fromBitmap(croppedImage, 0)
                    )
                    .addOnSuccessListener(result -> {
                        List<Text.TextBlock> textBlocks = result.getTextBlocks();
                        Rect textBoundingBox = null;
                        String tappedText = result.getText();
                        List<Pair<Rect, String>> candidates = new ArrayList<>();
                        for (Text.TextBlock block: textBlocks) {
                            Rect trialBoundingBox = block.getBoundingBox();
                            if (trialBoundingBox != null && trialBoundingBox.contains(tappedCoordinateX - bounds.left, tappedCoordinateY - bounds.top)) {
                                trialBoundingBox.offset(bounds.left, bounds.top);
                                candidates.add(new Pair<>(trialBoundingBox, block.getText()));
                            }
                        }

                        int minArea = Integer.MAX_VALUE;
                        for (Pair<Rect, String> value: candidates) {
                            int area = value.first.width() * value.first.height();
                            if (area < minArea) {
                                minArea = area;
                                textBoundingBox = value.first;
                                tappedText = value.second;
                            }
                        }
                        if (textBoundingBox != null) {
                            showResult(textBoundingBox, tappedText);
                        } else
                            showResult(bounds, tappedText);
                    }).addOnCompleteListener(tsk -> setIsProcessing(false));
        }, 100);
    }

    private void runForegroundWithNotification(String title, String description) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(new NotificationChannel("screen_capture", "Screen Capture Session", NotificationManager.IMPORTANCE_DEFAULT));
            startForeground(100, new Notification.Builder(NodeExplorerAccessibilityService.this, "screen_capture").
                    setContentTitle(title)
                    .setContentText(description)
                    .build()
            );
        } else {
            startForeground(100, new Notification.Builder(NodeExplorerAccessibilityService.this).
                    setContentTitle(title)
                    .setContentText(description)
                    .build()
            );
        }
    }
    
    private void requestMediaProjection() {
        overlayView.setVisibility(View.GONE);
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) overlayView.getLayoutParams();
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        windowManager.updateViewLayout(overlayView, params);

        mediaProjectionManager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        Intent screenCaptureIntent;
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
        } else {
            screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent();
        }
        Intent activityIntent = new Intent(this, MediaProjectionRequestActivity.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activityIntent.putExtra("screenCaptureIntent", screenCaptureIntent);

        LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                windowManager.updateViewLayout(overlayView, params);
                overlayView.setVisibility(View.VISIBLE);
                LocalBroadcastManager.getInstance(NodeExplorerAccessibilityService.this).unregisterReceiver(this);
                int resultCode = intent == null ? Activity.RESULT_CANCELED : intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
                if(resultCode != Activity.RESULT_OK) {
                    Toast.makeText(context, "Screen recording is needed to capture text on images", Toast.LENGTH_SHORT).show();
                    // if user rejected the media projection request revert back to text mode
                    toggleTextRecognitionMode(true);
                    return;
                }
                Intent data = intent.getParcelableExtra("data");
                runForegroundWithNotification("Capturing Screenshots", "session to capture screenshots");

                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                ScreenInfo screenInfo = getScreenConfiguration();

                imageReader = ImageReader.newInstance(screenInfo.width, screenInfo.height, PixelFormat.RGBA_8888, 1);
                mediaProjection.registerCallback(new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        super.onStop();
                        mediaProjectionDisplay.release();
                        imageReader.close();
                        mediaProjection = null;
                        mediaProjectionDisplay = null;
                        imageReader = null;
                        if (mediaProjectionIdleHandler != null) {
                            mediaProjectionIdleHandler.removeCallbacks(mediaProjectionIdlePostRunnable);
                        }
                        if (isTextRecognitionEnabled && overlayView != null && isWidgetExpanded) {
                            toggleTextRecognitionMode(true);
                        }
                    }
                }, null);
                mediaProjectionDisplay = mediaProjection.createVirtualDisplay(
                        "screenCapture",
                        screenInfo.width,
                        screenInfo.height,
                        screenInfo.densityDpi,  // screen density (adjust as needed)
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.getSurface(),
                        null,
                        null
                );
            }
        }, new IntentFilter("MEDIA_PROJECTION_DATA"));
        startActivity(activityIntent);
    }

    private void notifyStateOnQuickTile(boolean newState) {
        if (newState != sharedPreferences.getBoolean(Constants.TILE_ACTIVE_KEY, false))
            ShortcutTileLauncher.requestListeningState(this, new ComponentName(this, ShortcutTileLauncher.class));
    }

    private void isInsideElement(AccessibilityNodeInfo node, int coordinateX, int coordinateY) {
        if (node == null) return;
        Rect nodeBound = new Rect();
        node.getBoundsInScreen(nodeBound);
        if (nodeBound.contains(coordinateX, coordinateY)) {
            int area = nodeBound.height() * nodeBound.width();
            if (area < minArea || (area == minArea && !getText(node).isEmpty())) {
                selectedNode = node;
                minArea = area;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                isInsideElement(node.getChild(i), coordinateX, coordinateY);
            }
        } else if ((nodeBound.top == nodeBound.bottom)) {
            for (int i = 0; i < node.getChildCount(); i++) {
                isInsideElement(node.getChild(i), coordinateX, coordinateY);
            }
        }
    }

    private String getText(AccessibilityNodeInfo node) {
        CharSequence nodeText = node.getText();
        if (nodeText != null) {
            return nodeText.toString();
        }

        CharSequence contentDescription = node.getContentDescription();
        if (contentDescription != null) {
            return contentDescription.toString();
        }
        return "";
    }

    private void freeSearch(AccessibilityNodeInfo node) {
        Rect outBounds = new Rect();
        node.getBoundsInScreen(outBounds);
        String text = getText(node);
        if (!text.isEmpty())
            Log.d("text", text);
        for (int i = 0; i < node.getChildCount(); i++) {
            freeSearch(node.getChild(i));
        }
    }

    private void onStopWidget() {
        if (overlayView == null) return;
        if (systemNavigationButtonTapListener != null) {
            unregisterReceiver(systemNavigationButtonTapListener);
            systemNavigationButtonTapListener = null;
        }

        windowManager.removeView(overlayView);
        overlayView = null;
        modal = null;
        widgetController = null;
        clipboardManager = null;
        windowManager = null;

        if (mediaProjectionIdleHandler != null) {
            mediaProjectionIdleHandler.removeCallbacks(mediaProjectionIdlePostRunnable);
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
        if (!skipNotifyOnWidgetStop)
            notifyStateOnQuickTile(false);
        skipNotifyOnWidgetStop = false;
        isWidgetExpanded = false;
        isTextRecognitionEnabled = false;
        isAccessibilityServiceBusy = false;
    }

    static class NodeResult {
        public Rect bound;
        public String text;

        NodeResult(Rect bound, String text) {
            this.bound = bound;
            this.text = text;
        }
    }
    static abstract class NodeCapturedCallback {
        abstract void onResult(NodeResult result);
    }
    private void getNodeResult(float x, float y, boolean shouldRetrieveText, NodeCapturedCallback resultCallback) {
        isAccessibilityServiceBusy = true;
        new Thread(() -> {
            selectedNode = null;
            minArea = Integer.MAX_VALUE;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            isInsideElement(root, (int) x, (int) y);
//            freeSearch(root);
            String extractedText = !shouldRetrieveText || selectedNode == null ? "" : getText(selectedNode).trim();
            Rect bounds = new Rect();
            if (selectedNode != null)
                selectedNode.getBoundsInScreen(bounds);

            new Handler(Looper.getMainLooper()).post(() -> resultCallback.onResult(new NodeResult(bounds, extractedText)));
        }).start();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // when this service is created at phone-reboot or re-created by system my memory cleanup
        // it will check the current stale active state and turn it off.
        sharedPreferences = getSharedPreferences(Constants.SHARED_PREFERENCE_KEY, MODE_PRIVATE);
        if (sharedPreferences.getBoolean(Constants.TILE_ACTIVE_KEY, false))
            ShortcutTileLauncher.requestListeningState(this, new ComponentName(this, ShortcutTileLauncher.class));

        tileMessageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean shouldSkipNotify = intent.hasExtra(Constants.SKIP_NOTIFY_QUICK_TILE);
                if (intent.hasExtra(Constants.STOP_WIDGET)) {
                    if (shouldSkipNotify)
                        skipNotifyOnWidgetStop = true;
                    onStopWidget();
                } else if(!shouldSkipNotify) {
                    if (showFloatingWidget(false))
                        notifyStateOnQuickTile(true);
                } else
                    showFloatingWidget(true);
                }
        };
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(tileMessageReceiver, new IntentFilter(Constants.ACCESSIBILITY_SERVICE));
    }

    @Override
    public void onDestroy() {
        if (tileMessageReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(tileMessageReceiver);
            tileMessageReceiver = null;
        }
        super.onDestroy();
        onStopWidget();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {}

    @Override
    public void onInterrupt() {
        onStopWidget();
    }
}
