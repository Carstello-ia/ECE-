package engine;

/**
 * Keeps the kill combo of the current level (Requirement 8 - Gameplay HUD,
 * KAN-23): kills made in quick succession build a combo, and the combo
 * gives a score multiplier.
 *
 * It holds no drawing code. The game screen reports kills and hits, and
 * the draw manager asks it what to show. Every method takes the current
 * time explicitly, so the class can be driven by a fake clock in tests.
 */
public class ComboTracker {

	/** Max time, in ms, between two kills for the combo to keep going. */
	public static final long COMBO_WINDOW_MS = 1500;
	/** Combo needed for each multiplier tier (x2, x3, x4). */
	public static final int[] TIER_THRESHOLDS = { 5, 10, 20 };
	/** How long, in ms, the combo text grows after reaching a new tier. */
	public static final long TIER_POP_MS = 350;
	/** How long, in ms, the combo text fades out after the combo ends. */
	public static final long FADE_MS = 600;
	/** Smallest combo shown on screen. */
	public static final int MIN_SHOWN_COMBO = 2;

	/** Current combo, 0 when no combo is running. */
	private int combo;
	/** Time of the last kill, or -1. */
	private long lastKillAt = -1;
	/** Time the current tier was reached, or -1. */
	private long tierReachedAt = -1;
	/** Combo reached before the last reset, shown while fading. */
	private int endedCombo;
	/** Multiplier of the combo that ended, shown while fading. */
	private int endedMultiplier = 1;
	/** Time the last combo ended, or -1. */
	private long endedAt = -1;
	/** Best combo of this level. */
	private int bestCombo;
	/** Time since the last kill when the game was paused, or -1. */
	private long pausedElapsed = -1;

	/**
	 * Ends the combo if no kill came in time. Called once per frame.
	 *
	 * @param now
	 *            Current time in ms.
	 */
	public void update(final long now) {
		this.pausedElapsed = -1;
		if (this.combo > 0 && now - this.lastKillAt > COMBO_WINDOW_MS)
			end(now);
	}

	/**
	 * Stops the combo clock while the game is paused, so a pause never
	 * ends a combo. Called once per paused frame.
	 *
	 * @param now
	 *            Current time in ms.
	 */
	public void holdDuringPause(final long now) {
		if (this.combo == 0)
			return;
		if (this.pausedElapsed < 0)
			this.pausedElapsed = now - this.lastKillAt;
		this.lastKillAt = now - this.pausedElapsed;
	}

	/**
	 * Records a kill and returns the multiplier to apply to its points.
	 *
	 * @param now
	 *            Current time in ms.
	 * @return Score multiplier for this kill (1 to 4).
	 */
	public int registerKill(final long now) {
		update(now);
		int oldMultiplier = getMultiplier();
		this.combo++;
		this.lastKillAt = now;
		this.bestCombo = Math.max(this.bestCombo, this.combo);
		if (getMultiplier() > oldMultiplier)
			this.tierReachedAt = now;
		return getMultiplier();
	}

	/**
	 * Breaks the combo, e.g. when the player is hit.
	 *
	 * @param now
	 *            Current time in ms.
	 */
	public void breakCombo(final long now) {
		if (this.combo > 0)
			end(now);
	}

	/** Ends the current combo and starts its fade-out. */
	private void end(final long now) {
		if (this.combo >= MIN_SHOWN_COMBO) {
			this.endedCombo = this.combo;
			this.endedMultiplier = getMultiplier();
			this.endedAt = now;
		}
		this.combo = 0;
		this.tierReachedAt = -1;
	}

	/**
	 * @return Current combo, 0 when none is running.
	 */
	public int getCombo() {
		return this.combo;
	}

	/**
	 * @return Best combo of this level.
	 */
	public int getBestCombo() {
		return this.bestCombo;
	}

	/**
	 * @return Current score multiplier (1 to 4).
	 */
	public int getMultiplier() {
		return multiplierFor(this.combo);
	}

	/**
	 * @param combo
	 *            A combo value.
	 * @return Multiplier that combo gives (1 to 4).
	 */
	public static int multiplierFor(final int combo) {
		int multiplier = 1;
		for (int threshold : TIER_THRESHOLDS)
			if (combo >= threshold)
				multiplier++;
		return multiplier;
	}

	/**
	 * @param now
	 *            Current time in ms.
	 * @return Combo to show (the running one, or the one fading out), or 0
	 *         when nothing should be drawn.
	 */
	public int getShownCombo(final long now) {
		if (this.combo >= MIN_SHOWN_COMBO)
			return this.combo;
		if (isFading(now))
			return this.endedCombo;
		return 0;
	}

	/**
	 * @param now
	 *            Current time in ms.
	 * @return Multiplier that goes with {@link #getShownCombo}.
	 */
	public int getShownMultiplier(final long now) {
		if (this.combo >= MIN_SHOWN_COMBO)
			return getMultiplier();
		return this.endedMultiplier;
	}

	/**
	 * @param now
	 *            Current time in ms.
	 * @return Opacity of the combo text, from 0 (hidden) to 1.
	 */
	public float getAlpha(final long now) {
		if (this.combo >= MIN_SHOWN_COMBO)
			return 1f;
		if (!isFading(now))
			return 0f;
		return 1f - (float) (now - this.endedAt) / FADE_MS;
	}

	/**
	 * @param now
	 *            Current time in ms.
	 * @return Scale of the combo text: above 1 right after a new tier, back
	 *         to 1 after {@link #TIER_POP_MS}.
	 */
	public float getScale(final long now) {
		if (this.tierReachedAt < 0 || this.combo == 0)
			return 1f;
		long elapsed = now - this.tierReachedAt;
		if (elapsed >= TIER_POP_MS)
			return 1f;
		return 1f + 0.3f * (1f - (float) elapsed / TIER_POP_MS);
	}

	/**
	 * @param now
	 *            Current time in ms.
	 * @return Share of the combo window left before the combo ends, from 0
	 *         to 1 (0 when no combo is running).
	 */
	public float getTimeLeft(final long now) {
		if (this.combo == 0)
			return 0f;
		long left = COMBO_WINDOW_MS - (now - this.lastKillAt);
		return Math.max(0f, Math.min(1f, (float) left / COMBO_WINDOW_MS));
	}

	/** @return True while an ended combo is fading out. */
	private boolean isFading(final long now) {
		return this.endedAt >= 0 && now - this.endedAt < FADE_MS;
	}
}
