package com.example.myapplication.ui.statistics.askTimeline;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AskTimelineBottomSheetFragment extends BottomSheetDialogFragment implements ChatMessageAdapter.OnDateLinkClickListener {
    private RecyclerView rvChatMessages;
    private ChatMessageAdapter adapter;
    private EditText etMessageInput;
    private ImageButton btnSend;
    private ImageButton btnClearHistory;
    private LinearLayout layoutTypingIndicator;
    private View exampleQuestions;
    private Chip exampleQuestion1;
    private Chip exampleQuestion2;
    private Chip exampleQuestion3;

    private TimelineChatSession session;

    public static AskTimelineBottomSheetFragment newInstance() {
        return new AskTimelineBottomSheetFragment();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        
        // Adjust keyboard so it doesn't cover input controls
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        
        // Expand bottom sheet on display and cap height at 85% screen height
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            View bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                bottomSheet.getLayoutParams().height = (int) (screenHeight * 0.85);
                bottomSheet.requestLayout();
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_ask_timeline, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        session = TimelineChatSession.getInstance();

        rvChatMessages = view.findViewById(R.id.rv_chat_messages);
        etMessageInput = view.findViewById(R.id.et_message_input);
        btnSend = view.findViewById(R.id.btn_send);
        btnClearHistory = view.findViewById(R.id.btn_clear_history);
        layoutTypingIndicator = view.findViewById(R.id.layout_typing_indicator);
        exampleQuestions = view.findViewById(R.id.example_questions);

        exampleQuestion1 = view.findViewById(R.id.example_question_1);
        exampleQuestion2 = view.findViewById(R.id.example_question_2);
        exampleQuestion3 = view.findViewById(R.id.example_question_3);

        // Setup chat RecyclerView
        adapter = new ChatMessageAdapter(this);
        rvChatMessages.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvChatMessages.setAdapter(adapter);

        // Load chat history
        List<ChatMessage> history = session.getChatHistory();
        adapter.setMessages(history);

        // Show/hide example questions depending on whether history already exists
        if (history.size() > 1 && exampleQuestions != null) {
            exampleQuestions.setVisibility(View.GONE);
        } else if (exampleQuestions != null) {
            exampleQuestions.setVisibility(View.VISIBLE);
        }

        // chat bot typing indicator
        if (session.isQueryInProgress()) {
            layoutTypingIndicator.setVisibility(View.VISIBLE);
        } else {
            layoutTypingIndicator.setVisibility(View.GONE);
        }

        // Autoscroll to latest message if history exists
        if (!history.isEmpty()) {
            rvChatMessages.post(() -> {
                if (adapter.getItemCount() > 0) {
                    rvChatMessages.scrollToPosition(adapter.getItemCount() - 1);
                }
            });
        }

        // Clear history button action
        if (btnClearHistory != null) {
            btnClearHistory.setOnClickListener(v -> {
                session.clearHistory();
                List<ChatMessage> freshHistory = session.getChatHistory();
                adapter.setMessages(freshHistory);
                if (exampleQuestions != null) {
                    exampleQuestions.setVisibility(View.VISIBLE);
                }
                if (layoutTypingIndicator != null) {
                    layoutTypingIndicator.setVisibility(View.GONE);
                }
                Toast.makeText(requireContext(), "Chat history cleared", Toast.LENGTH_SHORT).show();
            });
        }

        // Send query listeners
        btnSend.setOnClickListener(v -> sendUserQuery());

        etMessageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendUserQuery();
                return true;
            }
            return false;
        });

        // Click listener for example questions
        View.OnClickListener exampleQuestionListener = v -> {
            if (v instanceof Chip) {
                String promptText = ((Chip) v).getText().toString();
                etMessageInput.setText(promptText);
                sendUserQuery();
            }
        };

        if (exampleQuestion1 != null) exampleQuestion1.setOnClickListener(exampleQuestionListener);
        if (exampleQuestion2 != null) exampleQuestion2.setOnClickListener(exampleQuestionListener);
        if (exampleQuestion3 != null) exampleQuestion3.setOnClickListener(exampleQuestionListener);
    }

    private void sendUserQuery() {
        if (session.isQueryInProgress()) {
            return;
        }

        String text = etMessageInput.getText() != null ? etMessageInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(text)) {
            return;
        }

        final int sessionVersion = session.getSessionVersion();

        etMessageInput.setText("");
        if (exampleQuestions != null) {
            exampleQuestions.setVisibility(View.GONE);
        }

        // Add user message to UI and history
        ChatMessage userMsg = new ChatMessage(
                UUID.randomUUID().toString(),
                text,
                true,
                System.currentTimeMillis(),
                new ArrayList<>()
        );
        session.addMessage(userMsg);
        adapter.addMessage(userMsg);
        rvChatMessages.smoothScrollToPosition(adapter.getItemCount() - 1);

        // Show chatbot typing indicator while waiting for response
        session.setQueryInProgress(true);
        layoutTypingIndicator.setVisibility(View.VISIBLE);

        Context context = getContext();
        if (context == null) {
            session.setQueryInProgress(false);
            layoutTypingIndicator.setVisibility(View.GONE);
            return;
        }

        // query prompt to the llm
        AskTimelineLlmClient.askTimeline(context, text).thenAccept(chatBotMessage -> {
            if (session.getSessionVersion() != sessionVersion) {
                return;
            }
            session.addMessage(chatBotMessage);
            session.setQueryInProgress(false);
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    layoutTypingIndicator.setVisibility(View.GONE);
                    adapter.addMessage(chatBotMessage);
                    if (adapter.getItemCount() > 0) {
                        rvChatMessages.smoothScrollToPosition(adapter.getItemCount() - 1);
                    }
                });
            }
        }).exceptionally(e -> {
            if (session.getSessionVersion() != sessionVersion) {
                return null;
            }
            session.setQueryInProgress(false);
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    layoutTypingIndicator.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Error generating answer.", Toast.LENGTH_SHORT).show();
                });
            }
            return null;
        });
    }

    @Override
    public void onDateLinkClick(DateLink dateLink) {
        if (dateLink != null && dateLink.getDate() != null && getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            dismiss(); // Close bottom sheet before navigating
            mainActivity.navigateToDate(dateLink.getDate());
        }
    }
}

