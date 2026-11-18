package engine;

/**
 * Keeps the time-based state of the gameplay HUD animations
 * (Requirement 8 - Gameplay HUD): score count-up and flash, life
 * lost/gained blink, low-life pulse, level start pulse and coin/diamond
 * gain flash.
 *
 * It holds no drawing code. The game screen feeds it the real values once
 * per frame through {@link #update}, and the draw manager asks it what to
 * show. Every method takes the current time explicitly, so the class can be
 * driven by a fake clock in tests.
 */
public class HudAnimator {

	/** How long, in ms, the score value stays highlighted after a gain. */
	public static final long SCORE_FLASH_MS = 300;
	/** Time constant, in ms, of the score count-up (smaller = faster). */
	public static final double SCORE_ROLL_MS = 120.0;
	/** How long, in ms, a lost or gained life slot blinks. */
	public static final long LIFE_BLINK_MS = 900;
	/** Blink half-period, in ms, of a lost or gained life slot. */
	public static final long LIFE_BLINK_PERIOD_MS = 100;
	/** Pulse half-period, in ms, of the low-life "LIVES" label. */
	public static final long LOW_LIFE_PULSE_MS = 400;
	/** Lives at or below which the low-life pulse is shown. */
	public static final int LOW_LIFE_THRESHOLD = 1;
	/** How long, in ms, the level value pulses when gameplay starts. */
	public static final long LEVEL_INTRO_MS = 1500;
	/** Pulse half-period, in ms, of the level value. */
	public static final long LEVEL_PULSE_MS = 250;
	/** How long, in ms, a coin or diamond value stays highlighted. */
	public static final long CURRENCY_FLASH_MS = 400;

	/** True until the first update, which only records the start values. */
	private boolean first = true;
	/** Time of the previous update. */
	private long lastUpdate;

	/** Score shown on screen; catches up with the real score. */
	private double displayedScore;
	/** Real score at the last update. */
	private int targetScore;
	/** Time until which the score is highlighted. */
	private long scoreFlashUntil;

	/** Lives at the last update. */
	private int lives;
	/** Time the last life was lost, or -1. */
	private long lifeLostAt = -1;
	/** Slot index (0-based) of the last life lost. */
	private int lostSlot;
	/** Time the last life was gained, or -1. */
	private long lifeGainedAt = -1;
	/** Slot index (0-based) of the last life gained. */
	private int gainedSlot;

	/** Time gameplay started for this level, or -1 if not started yet. */
	private long levelStartAt = -1;

	/** Coins at the last update. */
	private int coins;
	/** Time until which the coin value is highlighted. */
	private long coinFlashUntil;
	/** Diamonds at the last update. */
	private int diamonds;
	/** Time until which the diamond value is highlighted. */
	private long diamondFlashUntil;

	/**
	 * Feeds the current real values. Call once per frame.
	 *
	 * @param now
	 *            Current time in ms.
	 * @param score
	 *            Current score.
	 * @param newLives
	 *            Current lives.
	 * @param newCoins
	 *            Current coin balance.
	 * @param newDiamonds
	 *            Current diamond balance.
	 */
	public void update(final long now, final int score, final int newLives,
			final int newCoins, final int newDiamonds) {
		if (this.first) {
			this.first = false;
			this.lastUpdate = now;
			this.displayedScore = score;
			this.targetScore = score;
			this.lives = newLives;
			this.coins = newCoins;
			this.diamonds = newDiamonds;
			return;
		}

		updateScore(now, score);

		if (newLives < this.lives) {
			this.lifeLostAt = now;
			this.lostSlot = newLives;
		} else if (newLives > this.lives) {
			this.lifeGainedAt = now;
			this.gainedSlot = newLives - 1;
		}
		this.lives = newLives;

		if (newCoins > this.coins)
			this.coinFlashUntil = now + CURRENCY_FLASH_MS;
		this.coins = newCoins;

		if (newDiamonds > this.diamonds)
			this.diamondFlashUntil = now + CURRENCY_FLASH_MS;
		this.diamonds = newDiamonds;

		this.lastUpdate = now;
	}

	/**
	 * Moves the displayed score towards the real score. A drop (restart)
	 * snaps immediately; a gain counts up and triggers the flash.
	 */
	private void updateScore(final long now, final int score) {
		if (score > this.targetScore)
			this.scoreFlashUntil = now + SCORE_FLASH_MS;
		this.targetScore = score;

		if (score <= this.displayedScore) {
			this.displayedScore = score;
			return;
		}
		long elapsed = Math.max(0, now - this.lastUpdate);
		double step = (score - this.displayedScore)
				* Math.min(1.0, elapsed / SCORE_ROLL_MS);
		this.displayedScore = Math.min(score,
				this.displayedScore + Math.max(1.0, step));
	}

	/**
	 * Marks the moment gameplay starts (end of the countdown), which
	 * starts the level value pulse. Only the first call counts.
	 *
	 * @param now
	 *            Current time in ms.
	 */
	public void startLevel(final long now) {
		if (this.levelStartAt < 0)
			this.levelStartAt = now;
	}

	/** @return Score to print in the HUD. */
	public int getDisplayedScore() {
		return (int) this.displayedScore;
	}

	/**
	 * @param now
	 *            Current time in ms.
	 * @return True while the score value should be highlighted.
	 */
	public boolean isScoreFlashing(final long now) {
		return now < this.scoreFlashUntil;
	}

	/**
	 * @param now
	 *            Current time in ms.
	 * @return Slot index of the lost life to blink-draw this frame, or -1.
	 */
	public int getLostSlotToDraw(final long now) {
		if (this.lifeLostAt < 0 || now - this.lifeLostAt >= LIFE_BLINK_MS)
			return -1;
		return blinkOn(now - this.lifeLostAt, LIFE_BLINK_PERIOD_MS)
				? this.lostSlot : -1;
	}

	/**
	 * @param now
	 *            Current time in ms.
	 * @return Slot index of a freshly gained life to hide this frame (so it
	 *         blinks in), or -1.
	 */
	public int getGainedSlotToHide(final long now) {
		if (this.lifeGainedAt < 0
				|| now - this.lifeGainedAt >= LIFE_BLINK_MS)
			return -1;
		return blinkOn(now - this.lifeGainedAt, LIFE_BLINK_PERIOD_MS)
				? -1 : this.gainedSlot;
	}

	/**
	 * @param now
	 *            Current time in ms.
	 * @return True when the "LIVES" label should be drawn in the warning
	 *         colour this frame.
	 */
	public boolean isLowLifePulseOn(final long now) {
		if (this.lives <= 0 || this.lives > LOW_LIFE_THRESHOLD)
			return false;
		return blinkOn(now, LOW_LIFE_PULSE_MS);
	}

	/**
	 * @param now
	 *            Current time in ms.
	 * @return True when the level value should be highlighted this frame.
	 */
	public boolean isLevelPulseOn(final long now) {
		if (this.levelStartAt < 0
				|| now - this.levelStartAt >= LEVEL_INTRO_MS)
			return false;
		return blinkOn(now - this.levelStartAt, LEVEL_PULSE_MS);
	}

	/**
	 * @param now
	 *            Current time in ms.
	 * @return True while the coin value should be highlighted.
	 */
	public boolean isCoinFlashing(final long now) {
		return now < this.coinFlashUntil;
	}

	/**
	 * @param now
	 *            Current time in ms.
	 * @return True while the diamond value should be highlighted.
	 */
	public boolean isDiamondFlashing(final long now) {
		return now < this.diamondFlashUntil;
	}

	/** True during the first half of each period. */
	private static boolean blinkOn(final long elapsed, final long halfPeriod) {
		return (elapsed / halfPeriod) % 2 == 0;
	}
}
