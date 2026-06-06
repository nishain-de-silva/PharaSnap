package com.alchemedy.pharasnap.utils.Tutorial;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;
import com.alchemedy.pharasnap.models.TutorialAction;
import com.alchemedy.pharasnap.utils.FloatingWidget;

class TutorialSteps {
    protected ViewGroup primaryOverlay;
    protected FloatingWidget floatingWidget;
    protected final TutorialGuide.Step[] steps = new TutorialGuide.Step[] {
            new TutorialGuide.Step(TutorialAction.TAP_WIDGET_BUTTON, "This is your tutorial playground, Tap on this button to get started", R.id.toggleCollapse, TutorialGuide.POSITION.LEFT),
            new TutorialGuide.Step(TutorialAction.NEXT, "This is your floating widget. This button group can be moved freely on the screen", R.id.buttonContainer, TutorialGuide.POSITION.LEFT)
                    .beforeRun(this::resetTranslation),
            new TutorialGuide.Step(TutorialAction.MOVE_WIDGET_BUTTON, "hold this button and drag and move freely on the screen", R.id.toggleCollapse, TutorialGuide.POSITION.LEFT)
                    .highlightTarget()
                    .beforeRun(this::resetTranslation),
            new TutorialGuide.Step(TutorialAction.WIDGET_MOVE_FINISHED),
            new TutorialGuide.Step(TutorialAction.MULTIPLE_TEXT_SELECT, "Nice. When widget is expanded you can tap and select text on the screen. Tap and select 2 or more text blocks on the screen including text in image",  R.id.text_hint_container, TutorialGuide.POSITION.TOP)
                    .beforeRun(() -> {
                        floatingWidget.clearAllSelections(false);
            }),
            new TutorialGuide.Step(TutorialAction.NEXT, "This bottom bar indicates when you selected some text and indicates the current capture mode", R.id.text_hint_container, TutorialGuide.POSITION.TOP)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.MOVE_BOTTOM_BAR, "By the way, there is a text block under this yellow bar right now. Try hold and drag the bottom bar slightly up to reveal that text", R.id.text_hint_container, TutorialGuide.POSITION.TOP)
                    .beforeRun(() -> {
                new MessageHandler(primaryOverlay.getContext()).sendBroadcast(
                        new Intent(Constants.TUTORIAL)
                                .putExtra(Constants.TUTORIAL_PLAYGROUND_STEP, 1)
                );
            }).highlightTarget(),
            new TutorialGuide.Step(TutorialAction.MOVE_FINISHED_BOTTOM_BAR),
            new TutorialGuide.Step(TutorialAction.SELECT_HIDDEN_TEXT,"There it is! Now tap and select that text block", R.id.text_hint_container, TutorialGuide.POSITION.TOP),
            new TutorialGuide.Step(TutorialAction.BOTTOM_BAR_SELECT, "Ok but we only need specific text from the whole text you have selected. Tap on bottom yellow bar again to view the text you have selected", R.id.text_hint_container, TutorialGuide.POSITION.TOP)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.MODAL_OPENED),
            new TutorialGuide.Step(
                    TutorialAction.NEXT,
                    "Here are the text you have selected from multiple text blocks. the order of the text is based on position of the text blocks on the screen from top to bottom and left to right",
                    R.id.action_copy_entire_text, TutorialGuide.POSITION.TOP
            ).beforeRun(this::resetTranslation),
            new TutorialGuide.Step(TutorialAction.SELECT_FEW_WORDS_ON_TEXT_SELECTION, "select one or two words you are interested", R.id.text_selector_scrollview_container, TutorialGuide.POSITION.TOP),

            new TutorialGuide.Step(TutorialAction.BROWSER_OPENING, "You can also quickly browse the text you selected in the internet with quick browser. Tap on it", R.id.action_search_web, TutorialGuide.POSITION.TOP)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.PRIMARY_OVERLAY_CHANGED),
            new TutorialGuide.Step(TutorialAction.MODAL_CLOSE, "Handy right? Ok now close this browser by tapping outside", R.id.browser_container, TutorialGuide.POSITION.TOP),
            new TutorialGuide.Step(TutorialAction.PRIMARY_OVERLAY_CHANGED),
            new TutorialGuide.Step(TutorialAction.START_EDIT_TEXT_CONTENT, "You can also edit the selected text content before you add them on clipboard. Tap on the button", R.id.action_change_text_content, TutorialGuide.POSITION.TOP)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.PRIMARY_OVERLAY_CHANGED),
            new TutorialGuide.Step(TutorialAction.MODAL_CLOSE, "After you changed the text you save the changes or press back dismiss the changes", R.id.modal_back, TutorialGuide.POSITION.RIGHT)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.PRIMARY_OVERLAY_CHANGED),
            new TutorialGuide.Step(TutorialAction.MODAL_CLOSE, "Alright now let's move on to the next feature. Close this modal as well", R.id.modal_back, TutorialGuide.POSITION.RIGHT)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.MODAL_CLOSED),
            new TutorialGuide.Step(TutorialAction.NEXT, "Note to remember: If you close this floating widget just after you selected text from screen without tapping this bar then by default all of that text will be copied to the clipboard", R.id.text_hint_container, TutorialGuide.POSITION.TOP),
            new TutorialGuide.Step(TutorialAction.ERASE_ALL_SELECTIONS, "let's tidy up the screen a bit. Press this eraser button to clear all the selections", R.id.eraser, TutorialGuide.POSITION.LEFT)
                    .highlightTarget(),
            new TutorialGuide.Step(
                    TutorialAction.LONG_PRESS_WORD,
                    "Here is another cool trick! You can quickly find meaning of an interested word in the screen by hold pressing the word in the screen. Try it now, hold any single word on the screen",
                    R.id.text_hint_container,
                    TutorialGuide.POSITION.TOP
            ),
            new TutorialGuide.Step(TutorialAction.BROWSER_OPENING),
            new TutorialGuide.Step(TutorialAction.PRIMARY_OVERLAY_CHANGED),
            new TutorialGuide.Step(TutorialAction.MODAL_CLOSE,
                    "Good job! now close this modal by pressing back or tapping outside to dismiss the browser",
                    R.id.webView,
                    TutorialGuide.POSITION.TOP
            ),
            new TutorialGuide.Step(TutorialAction.PRIMARY_OVERLAY_CHANGED),
            new TutorialGuide.Step(
                    TutorialAction.NEXT,
                    "The image poster you see has a content description (not the text you see on the image). But if you tap on the image then by default PharaSnap try to capture text on the screen instead of the content description.",
                    0,
                    TutorialGuide.POSITION.TOP
            )
                    .fetchViewDynamically()
                    .beforeRun(() -> {
                new MessageHandler(primaryOverlay.getContext()).sendBroadcast(
                        new Intent(Constants.TUTORIAL)
                                .putExtra(Constants.TUTORIAL_PLAYGROUND_STEP, 2)
                );
            }),
            new TutorialGuide.Step(TutorialAction.OPEN_QUICK_SETTINGS, "But that's not what we want! Alright tap on this setting button to toggle text capturing strategy", R.id.textCaptureMode, TutorialGuide.POSITION.LEFT)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.MODAL_OPENED),
            new TutorialGuide.Step(TutorialAction.NEXT, "In auto mode, PharaSnap will determine and prioritize to read system provided accessibility text or recognize image text with OCR based on if the element you have selected on the screen is an image. But if you want to specifically want to select either the system provided text or use only only image character recognition you can configure it in here.", R.id.mode_tab_selector, TutorialGuide.POSITION.TOP),
            new TutorialGuide.Step(TutorialAction.SELECT_TEXT_ONLY_MODE, "In this scenario we specifically want to read content description and not to recognize text placed on the image, therefore select the second option", R.id.modal_container, TutorialGuide.POSITION.TOP),
            new TutorialGuide.Step(TutorialAction.MODAL_CLOSE), // expect automatic closing
            new TutorialGuide.Step(TutorialAction.MODAL_CLOSED),
            new TutorialGuide.Step(TutorialAction.TAP_IMAGE_WITH_CONTENT_DESCRIPTION, "Ok now tap on the image poster on the screen again", 0, TutorialGuide.POSITION.TOP)
                    .fetchViewDynamically(),
            new TutorialGuide.Step(TutorialAction.BOTTOM_BAR_SELECT, "Tap on bottom bar again and check the text selection", R.id.text_hint_container, TutorialGuide.POSITION.TOP)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.MODAL_OPENED),
            new TutorialGuide.Step(TutorialAction.NEXT, "Here is the content description of that image. In rare cases PharaSnap auto mode will fail to identify the text you meant to capture therefore use this setting to manually specify text capturing strategy.", R.id.action_copy_entire_text, TutorialGuide.POSITION.TOP),
            new TutorialGuide.Step(TutorialAction.MODAL_CLOSE, "There is a bit more than text selection, close this modal to continue", R.id.modal_back, TutorialGuide.POSITION.RIGHT)
                    .beforeRun(() -> {
                        floatingWidget.clearAllSelections(false);
                    })
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.MODAL_CLOSED),
            new TutorialGuide.Step(TutorialAction.SWITCH_MODE, "In addition to text capturing you can also capture cropped screenshot. Tap on this button to toggle and switch to picture mode", R.id.toggleMode, TutorialGuide.POSITION.LEFT)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.PICTURE_SELECT, "In this mode, you can capture the image of specific object in the screen like a photo, image poster. It is like a screenshot of only specific object on the screen. Go ahead tap on the image poster", 0, TutorialGuide.POSITION.TOP)
                    .fetchViewDynamically(),
            new TutorialGuide.Step(TutorialAction.NEXT, "Image is saved! You can see that image in your image gallery after the tutorial", 0, TutorialGuide.POSITION.TOP)
                    .fetchViewDynamically(),
            new TutorialGuide.Step(TutorialAction.SWITCH_MODE, "Alright now switch back to text mode again", R.id.toggleMode, TutorialGuide.POSITION.LEFT)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.HISTORY_BUTTON_SELECT, "Ok, let's move on to the last feature. Tap on this button to view your recent copied items.", R.id.list, TutorialGuide.POSITION.LEFT).highlightTarget(),
            new TutorialGuide.Step(TutorialAction.MODAL_OPENED),
            new TutorialGuide.Step(TutorialAction.SELECT_RECENT_ITEM, "Tap on one of the item to see more functions", R.id.entry_list, TutorialGuide.POSITION.TOP),
            new TutorialGuide.Step(TutorialAction.NEXT, "and again you can use all the functions as before", 0, TutorialGuide.POSITION.CENTER),
            new TutorialGuide.Step(TutorialAction.NEXT, "Alright! That's a bit a lot. You have completed the all essentials, remember to check the settings section on the app to explore more features. Tutorial is complete!", 0, TutorialGuide.POSITION.CENTER)
    };

    private void resetTranslation() {
        View buttonContainer = primaryOverlay.findViewById(R.id.buttonContainer);
        buttonContainer.setTranslationX(0);
        buttonContainer.setTranslationY(0);
        primaryOverlay.findViewById(R.id.text_hint_container).setTranslationY(0);
    }
}
