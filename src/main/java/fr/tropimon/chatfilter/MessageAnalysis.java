package fr.tropimon.chatfilter;

import java.util.Optional;

/** Faits immuables d'une ligne, jamais sa visibilité ou l'état de la conversation. */
public record MessageAnalysis(
        String raw, Optional<PrivateMessageParser.Parsed> privateMessage,
        boolean staff, boolean town, Optional<String> townName, Optional<String> townSender,
        boolean channelConfirmation, boolean noTown, GlobalMessageCategory category,
        Optional<String> speaker, Optional<GroupMessageProtocol.Payload> groupPayload) {

    public static MessageAnalysis analyze(String raw, String localPlayer) {
        String message = raw == null ? "" : raw;
        var privateMessage = PrivateMessageParser.parse(message, localPlayer);
        var details = ChatMessageClassifier.details(message);
        var groupSender = GroupMessageProtocol.displaySender(message);
        Optional<String> speaker;
        if (groupSender.isPresent()) {
            speaker = groupSender;
        } else if (privateMessage.isPresent()) {
            speaker = privateMessage.get().incoming()
                    ? Optional.of(privateMessage.get().counterpart()) : Optional.ofNullable(localPlayer);
        } else if (details.sender().isPresent()) {
            speaker = ChatMessageClassifier.isSelfDestination(details.sender().get())
                    ? Optional.ofNullable(localPlayer) : details.sender();
        } else {
            speaker = ClickableUsername.findParsed(message, privateMessage).map(ClickableUsername.Match::name);
        }
        return new MessageAnalysis(message, privateMessage, details.staff(), details.town(),
                details.name(), details.sender(), ChatMessageClassifier.isChannelChangeConfirmation(message),
                TownStatusParser.isNoTownMessage(message), GlobalMessageClassifier.classify(message),
                speaker, GroupMessageProtocol.parse(message));
    }

    public boolean sentByLocal(String localPlayer) {
        return townSender.map(sender -> ChatMessageClassifier.isSelfDestination(sender)
                || localPlayer != null && sender.equalsIgnoreCase(localPlayer)).orElse(false);
    }
}
