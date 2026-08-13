package com.aiassistant.channel;
import com.aiassistant.channel.confirmation.WriteConfirmation;
public interface ConfirmationNotifier { void notify(WriteConfirmation confirmation); }
