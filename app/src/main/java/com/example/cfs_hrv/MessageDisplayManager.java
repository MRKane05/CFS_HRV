package com.example.cfs_hrv;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import java.util.Arrays;
import java.util.List;

public class MessageDisplayManager {

    private TextView messageTextView;
    private Handler handler;
    private int currentMessageIndex = 0;
    private List<String> currentStageMessages;
    private long fadeDuration = 500; // Duration for fade in/out in milliseconds
    private long displayDuration = 5000; // How long message stays visible

    // Define messages for each stage
    private List<String> stage1Messages = Arrays.asList(
            "Welcome. It's best to record data before you even get out of bed",
            "Get comfortable and press button to begin",
            "Try to be positioned so that you can breathe easily",
            "Moving while taking a recording can cause noise in the data"
    );

    private List<String> stage2Messages = Arrays.asList(
            "Get settled and comfortable",
            "Try to get a saw-tooth pattern before starting the record",
            "Wait until graph stabilizes before starting a record",
            "Try repositioning your finger if the camera view isn't red",
            "Try varying degrees of pressure on the screen to get a stable result",
            "At worst try a different position/hand for doing a measure",
            "Depending on the light levels around your phone it can be difficult to get a good measure",
            "If you cannot get a good measure happening try moving/stretching your hand to promote blood flow"
    );

    private List<String> stage3Messages = Arrays.asList(
            "Recording your Heart Rate data",
            "Breathe normally, keep still, even yawning can add noise to the recording",
            "The progress bar is set to record 2 minutes of data in order to get a more accurate measure",
            "The more good data you can get the better your data will be for predictions",
            "This approach is based off of studies linking heart rate variability to fatigue levels",
            "If the data you record now seems out of character you can simply repeat the measure",
            "Measures can be done, or re-done at any stage of the day, but you must make sure your symptoms are correctly updated",
            "According to published studies your HRV will stabilize as the day goes on",
            "It does feel like a long time but try to relax and stick with it",
            "This is only to guess how much you've got in the tank for the day, it doesn't have to be perfect"
    );

    // Constructor
    public MessageDisplayManager(TextView messageTextView) {
        this.messageTextView = messageTextView;
        this.handler = new Handler(Looper.getMainLooper());
    }

    // Start displaying messages for a specific stage
    public void startStage(int stage) {
        handler.removeCallbacksAndMessages(null);
        switch (stage) {
            case 1:
                currentStageMessages = stage1Messages;
                break;
            case 2:
                currentStageMessages = stage2Messages;
                break;
            case 3:
                currentStageMessages = stage3Messages;
                break;
            default:
                currentStageMessages = Arrays.asList();
        }
        currentMessageIndex = 0;

        if (!currentStageMessages.isEmpty()) {
            showNextMessage();
        }
    }

    // Stop displaying messages
    public void stop() {
        handler.removeCallbacksAndMessages(null);
        fadeOut(messageTextView, new Runnable() {
            @Override
            public void run() {
                messageTextView.setVisibility(View.INVISIBLE);
            }
        });
    }

    // Show the next message in the sequence
    private void showNextMessage() {
        if (currentMessageIndex >= currentStageMessages.size()) {
            currentMessageIndex = 0; // Loop back to start
        }

        final String message = currentStageMessages.get(currentMessageIndex);

        // Fade out current message, then fade in new one
        fadeOut(messageTextView, new Runnable() {
            @Override
            public void run() {
                messageTextView.setText(message);
                fadeIn(messageTextView, new Runnable() {
                    @Override
                    public void run() {
                        // After message is visible, wait displayDuration, then show next
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                currentMessageIndex++;
                                showNextMessage();
                            }
                        }, displayDuration);
                    }
                });
            }
        });
    }

    // Fade in animation
    private void fadeIn(View view, final Runnable onComplete) {
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate()
                .alpha(1f)
                .setDuration(fadeDuration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    }
                });
    }

    // Fade out animation
    private void fadeOut(View view, final Runnable onComplete) {
        view.animate()
                .alpha(0f)
                .setDuration(fadeDuration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setVisibility(View.INVISIBLE);
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    }
                });
    }
}