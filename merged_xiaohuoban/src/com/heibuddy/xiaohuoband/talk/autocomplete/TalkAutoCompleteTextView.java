package com.heibuddy.xiaohuoband.talk.autocomplete;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.AutoCompleteTextView;

public class TalkAutoCompleteTextView extends AutoCompleteTextView {

	public TalkAutoCompleteTextView(Context context) {
		super(context);
	}

	public TalkAutoCompleteTextView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public TalkAutoCompleteTextView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	private BackButtonPressedEventListener backButtonPressedEventListener;

	public void setOnBackButtonPressedEventListener(BackButtonPressedEventListener eventListener) {
		backButtonPressedEventListener = eventListener;
	}
	
	public String getTrimmedText(){
		return getText().toString().trim();
	}

	@Override
	public boolean onKeyPreIme(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
			backButtonPressedEventListener.onBackButtonPressed();
			return false;
		}
		return super.dispatchKeyEvent(event);
	}
	
	private boolean lastCharIsSpaceOrNull(){
		return !hasText() || getText().charAt(getText().length() - 1) == ' ';
	}

	public boolean hasText() {
		return getText().length() > 0;
	}

	private boolean isCursorAtEnd() {
		return getSelectionStart() == getText().length();
	}

	public void addTextWithTrailingSpace(String phrase) {
		setText(phrase.trim() + " ");
        setCursorAtEnd();
    }

    private void setCursorAtEnd() {
        setSelection(getText().length());
    }

    public void pasteQuery(String suggestion) {
        releaseFocus();
        setText(suggestion);
        append(" ");
        obtainFocus();
        setCursorAtEnd();
    }

    private void releaseFocus() {
        setFocusable(false);
        setFocusableInTouchMode(false);
    }

    private void obtainFocus() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        this.requestFocus();
    }

}
