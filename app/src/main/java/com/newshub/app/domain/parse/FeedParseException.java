package com.newshub.app.domain.parse;

/** Thrown when a downloaded document cannot be turned into a list of publications. */
public class FeedParseException extends Exception {

    public enum Kind {
        /** The server returned an empty body. */
        EMPTY,
        /** The document is an HTML page or another non-feed document. */
        NOT_A_FEED,
        /** The document is broken beyond what the lenient parser can recover. */
        MALFORMED
    }

    private static final long serialVersionUID = 1L;

    public final Kind kind;

    public FeedParseException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }
}
