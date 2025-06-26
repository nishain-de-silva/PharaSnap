package com.alchemedy.pharasnap.utils;

public class TutorialGuide {
    public static String START_TUTORIAL = "startTutorial";
    public static String TAPPED_WIDGET_BUTTON = "tappedWidgetButton";
    public static String MOVED_WIDGET_BUTTON = "movedWidgetButton";
    public static String MULTIPLE_TEXT_SELECTED = "multipleTextSelected";
    public static String BOTTOM_BAR_SELECTED = "bottomBarSelected";
    public static String MODAL_CLOSED = "modalClosed";
    public static String ERASED_ALL_SELECTIONS = "erasedAllSelections";
    public static String BROWSER_OPENED_AS_DICTIONARY = "browserOpenedAsDictionary";
    public static String IMAGE_WITH_ACCESSIBILITY_TEXT = "imageWithAccessibilityText";
    public static String OPENED_QUICK_SETTINGS = "openQuickSettings";
    public static String SELECTED_OCR_MODE = "selectedOCRMode";
    public static String NEXT = "next";
    public static String SWITCHED_PICTURE_MODE = "switchedToPictureMode";
    public static String PICTURE_SELECTED = "pictureSelected";
    public static String HISTORY_BUTTON_SELECTED = "historyButtonSelected";

    private static TutorialGuide instance;

    public static void start() {
        instance = new TutorialGuide();
    }
    private String[] steps = new String[] {
            START_TUTORIAL, "Tap on this widget button, You can move this widget",
            TAPPED_WIDGET_BUTTON, "Here are your buttons controls. You can drag this widget anywhere on the screen, Try moving the widget a bit.",
            MOVED_WIDGET_BUTTON, "Good. Now try to selected 2 text blocks from the screen",
            MULTIPLE_TEXT_SELECTED, "Great, as you have noticed this bottom bar indicates your current mode or other information. Tap on this view the coped selection",
            BOTTOM_BAR_SELECTED, "You can edit text selection, you can select a word by tapping - no need to hold press. You can either google the selection or copy the selection to clipboard. If you want to change the content of text selection you can edit the text before you copy",
            NEXT, "Ok, now close this modal/dialog, you can also tap on the background to dismiss",
            MODAL_CLOSED, "press the eraser button to removal all selections",
            ERASED_ALL_SELECTIONS, "Alright, let's show you another trick! You can instantly google a word definition by hold pressing a single word in the screen. Try it now",
            BROWSER_OPENED_AS_DICTIONARY, "This is the pop-over browser where you can conveniently browse the text selection",
            NEXT, "Ok, close this browser now. Tap back press or tap on the background",
            MODAL_CLOSED, "Ok, now tap on the image with text - \"Correct image text\"",
            IMAGE_WITH_ACCESSIBILITY_TEXT, "now tap again on the bottom bar as before",
            BOTTOM_BAR_SELECTED, "Hmm...the captured text wrong. But fortunately there is an way to fix this, close this modal",
            MODAL_CLOSED, "Go to quick setting, tap on the cogwheel button",
            OPENED_QUICK_SETTINGS, "PharaSnap will capture the provided accessibility text or recognize text with OCR based on if the tapped element is an image. But in some cases PharaSnap may fails to identify if the tapped element is the image like what happened right now." +
            "\nTo fix it you have to choose manual text capture method",
            NEXT, "Go ahead select the third option in the menu",
            SELECTED_OCR_MODE, "Alright now close this modal and re-select that text block again",
            IMAGE_WITH_ACCESSIBILITY_TEXT, "Ok check the selected text by tapping on bottom bar",
            BOTTOM_BAR_SELECTED, "See, It got it correct this time. Now let's cover the last bits, close this modal",
            MODAL_CLOSED, "In addition to text capturing you can also capture cropped screenshot. Tap on the button with T icon to switch to picture mode",
            SWITCHED_PICTURE_MODE, "Ok now tap on any image on the screen to saved that particular image on your device gallery",
            PICTURE_SELECTED, "You can view history of your copied items. Tap on the history button",
            HISTORY_BUTTON_SELECTED, "Alright. You have completed the all essentials"
    };
}
