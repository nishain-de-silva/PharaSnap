package com.alchemedy.pharasnap.utils.Tutorial;

import android.content.Intent;
import android.view.View;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;
import com.alchemedy.pharasnap.models.TutorialAction;
import com.alchemedy.pharasnap.widgets.CustomOverlayView;

class TutorialSteps {
    protected CustomOverlayView rootOverlay;
    protected final TutorialGuide.Step[] steps = new TutorialGuide.Step[] {
            new TutorialGuide.Step(TutorialAction.TAP_WIDGET_BUTTON, "Tap on this button to expand the widget. You can hold and move this as well", R.id.toggleCollapse, TutorialGuide.POSITION.LEFT),
            new TutorialGuide.Step(TutorialAction.NEXT, "Here are your floating widget's buttons controls. This button group can be moved freely on the screen", R.id.buttonContainer, TutorialGuide.POSITION.LEFT),
            new TutorialGuide.Step(TutorialAction.MOVE_WIDGET_BUTTON, "hold this button  then drag and move move it along on the screen!", R.id.toggleCollapse, TutorialGuide.POSITION.LEFT).highlightTarget(),
            new TutorialGuide.Step(TutorialAction.WIDGET_MOVE_FINISHED),
            new TutorialGuide.Step(TutorialAction.MULTIPLE_TEXT_SELECT, "Nice. When widget is expanded you can select text on the screen just by tapping on it. Tap and select 2 or more text blocks to continue, you can even select text on images",  R.id.text_hint_container, TutorialGuide.POSITION.TOP),
            new TutorialGuide.Step(TutorialAction.NEXT, "The bottom bar is the main way give information and alert you when using the this widget. This bar indicates your current mode and whenever you have selected some text", R.id.text_hint_container, TutorialGuide.POSITION.TOP)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.MOVE_BOTTOM_BAR, "You can also drag and move this bar if it blocks an underneath text. By the way there is a text block under this bar try hold and drag the bar slightly up to reveal the text", R.id.text_hint_container, TutorialGuide.POSITION.TOP)
                    .beforeRun(() -> {
                new MessageHandler(rootOverlay.getContext()).sendBroadcast(
                        new Intent(Constants.TUTORIAL)
                                .putExtra(Constants.SHOW_HIDDEN_TEXT_IN_TUTORIAL, 1)
                );
            }).highlightTarget(),
            new TutorialGuide.Step(TutorialAction.MOVE_FINISHED_BOTTOM_BAR),
            new TutorialGuide.Step(TutorialAction.SELECT_HIDDEN_TEXT,"There it is! Now tap and select that text block", R.id.text_hint_container, TutorialGuide.POSITION.TOP),
            new TutorialGuide.Step(TutorialAction.NEXT, "Whenever you have selected some text is bar indicates you. Then you can tap on bar to view all your selected text and adjust the text you want to copy", R.id.text_hint_container, TutorialGuide.POSITION.TOP)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.BOTTOM_BAR_SELECT, "Remember you can skip step on tapping on this bar and collapse/stop this widget and all the selected text will be copied to clipboard. Alright anyway let's tap on this bar now", R.id.text_hint_container, TutorialGuide.POSITION.TOP)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.MODAL_OPENED),
            new TutorialGuide.Step(
                    TutorialAction.NEXT,
                    "In this window you and adjust and select the text you want to copy to copy to clipboard. Note that text selection is bit different here, you don't have hold wait to select an word instead you can just tap on it and drag and select all you want.The cursor will automatically select the whole word as you move the cursor on them.",
                    R.id.action_copy_entire_text, TutorialGuide.POSITION.TOP
            ).beforeRun(() -> {
                View buttonContainer = rootOverlay.findViewById(R.id.buttonContainer);
                buttonContainer.setTranslationX(0);
                buttonContainer.setTranslationY(0);
                rootOverlay.findViewById(R.id.text_hint_container).setTranslationY(0);
            }),
            new TutorialGuide.Step(TutorialAction.NEXT, "You can edit the text content, maybe add your own words before you select the text", R.id.action_change_text_content, TutorialGuide.POSITION.TOP)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.NEXT, "Sometimes you want to quickly google on some words you have interested and this is the feature to easily browse the words you have selected", R.id.action_search_web, TutorialGuide.POSITION.TOP)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.MODAL_CLOSE, "Alright now let's move on to the next feature. Close this modal/dialog to continue, you can also tap on the background/backdrop to dismiss this modal", R.id.modal_back, TutorialGuide.POSITION.RIGHT)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.MODAL_CLOSED),
            new TutorialGuide.Step(TutorialAction.ERASE_ALL_SELECTIONS, "let's tidy up the screen a bit. Press this eraser button to clear all the selections", R.id.eraser, TutorialGuide.POSITION.LEFT)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.LONG_PRESS_WORD, "Alright, let's show you another trick! You can instantly google a word definition by hold pressing a single word in the screen. Try it now, hold any word on the screen", R.id.text_hint_container, TutorialGuide.POSITION.TOP),
            new TutorialGuide.Step(TutorialAction.MODAL_OPENED),
            new TutorialGuide.Step(TutorialAction.NEXT, "Handy isn't it This is the pop-over browser where you can conveniently browse the interested text", R.id.webView, TutorialGuide.POSITION.TOP),
            new TutorialGuide.Step(TutorialAction.MODAL_CLOSE, "Tap back press or tap on the background to close this browser", R.id.webView, TutorialGuide.POSITION.TOP),
            new TutorialGuide.Step(TutorialAction.MODAL_CLOSED),
            new TutorialGuide.Step(TutorialAction.NEXT, "As you can see there is an image poster in the screen. This image might have a content description - not the text you see on the image. But if you tap on the image it PharaSnap will try to capture text on the screen instead of the content description.", R.id.text_hint_container, TutorialGuide.POSITION.TOP),
            new TutorialGuide.Step(TutorialAction.OPEN_QUICK_SETTINGS, "Tap to quick setting button to temporary adjust the text capture strategy", R.id.textCaptureMode, TutorialGuide.POSITION.LEFT)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.MODAL_OPENED),
            new TutorialGuide.Step(TutorialAction.NEXT, "In auto mode, PharaSnap will determine and prioritize to read accessibility text or recognize image text with OCR based on if the element you have selected on the screen is an image. But if you want to explicitly want to select only the accessibility text or the capture text only using the OCR you can configure it in here.", R.id.mode_tab_selector, TutorialGuide.POSITION.TOP),
            new TutorialGuide.Step(TutorialAction.SELECT_TEXT_ONLY_MODE, "In this scenario we want to read content description or the accessibility text given by the image so go ahead select the second option", R.id.mode_tab_selector, TutorialGuide.POSITION.TOP),
            new TutorialGuide.Step(TutorialAction.MODAL_CLOSE), // expect automatic closing
            new TutorialGuide.Step(TutorialAction.MODAL_CLOSED),
            new TutorialGuide.Step(TutorialAction.TAP_IMAGE_WITH_CONTENT_DESCRIPTION, "Ok now select the image poster on the screen", R.id.text_hint_container, TutorialGuide.POSITION.TOP),
            new TutorialGuide.Step(TutorialAction.BOTTOM_BAR_SELECT, "Tap on bottom bar again and check the text selection", R.id.text_hint_container, TutorialGuide.POSITION.TOP)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.MODAL_OPENED),
            new TutorialGuide.Step(TutorialAction.NEXT, "Here is the content description of that image. In rare cases PharaSnap will fail to identify the element you have selected on the screen is an image and mistakenly prioritize and select the accessibility text instead of recognizing text on the image, in that case you can do the opposite and explicitly to use OCR mode only", R.id.action_copy_entire_text, TutorialGuide.POSITION.TOP),
            new TutorialGuide.Step(TutorialAction.MODAL_CLOSE, "There is a bit more than text selection, close this modal to continue", R.id.modal_back, TutorialGuide.POSITION.RIGHT)
                    .beforeRun(() -> {
                        rootOverlay.clearAllSelections();
                    })
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.MODAL_CLOSED),
            new TutorialGuide.Step(TutorialAction.SWITCH_MODE, "In addition to text capturing you can also capture cropped screenshot. Tap on this button to toggle and switch to picture mode", R.id.toggleMode, TutorialGuide.POSITION.LEFT)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.PICTURE_SELECT, "In this mode instead of selecting text you can capture the image of the element you have selected or rather a cropped screen. Go ahead tap on the image poster and save it to your photo gallery", R.id.text_hint_container, TutorialGuide.POSITION.TOP),
            new TutorialGuide.Step(TutorialAction.SWITCH_MODE, "Alright, switch back to text mode again", R.id.toggleMode, TutorialGuide.POSITION.LEFT)
                    .highlightTarget(),
            new TutorialGuide.Step(TutorialAction.MULTIPLE_TEXT_SELECT, "Tap and select another 2 more text blocks on the screen again.", R.id.text_hint_container, TutorialGuide.POSITION.TOP),
            new TutorialGuide.Step(TutorialAction.HISTORY_BUTTON_SELECT, "Good you can check that image after the tutorial. You can also view history of your select items. Tap on the history button to view your recent copied text selections", R.id.list, TutorialGuide.POSITION.LEFT).highlightTarget(),
            new TutorialGuide.Step(TutorialAction.MODAL_OPENED),
            new TutorialGuide.Step(TutorialAction.SELECT_RECENT_ITEM, "Tap on one of the item - not the copy button on the edge inside the item", 0, TutorialGuide.POSITION.CENTER),
            new TutorialGuide.Step(TutorialAction.NEXT, "Here again you can again select the text you want to copy but this does not change this item itself on the history.", 0, TutorialGuide.POSITION.CENTER),
            new TutorialGuide.Step(TutorialAction.NEXT, "Phew! That's a bit a lot. You have completed the all essentials, remember to check the settings section on the app to explore more features. Press tap to finish", 0, TutorialGuide.POSITION.CENTER)
    };
}
