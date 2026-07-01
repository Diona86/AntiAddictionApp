package com.exampl.antiaddiction.viewmodel;

public abstract class StatIntent {

    public static final class Refresh extends StatIntent {}

    public static final class ResumeAutoRefresh extends StatIntent {}

    public static final class PauseAutoRefresh extends StatIntent {}

    public static final class SubmitTotalLimit extends StatIntent {
        public final String userId;
        public final int minutes;

        public SubmitTotalLimit(String userId, int minutes) {
            this.userId = userId;
            this.minutes = minutes;
        }
    }

    public static final class SubmitAppLimit extends StatIntent {
        public final String userId;
        public final String packageName;
        public final int minutes;

        public SubmitAppLimit(String userId, String packageName, int minutes) {
            this.userId = userId;
            this.packageName = packageName;
            this.minutes = minutes;
        }
    }
}
