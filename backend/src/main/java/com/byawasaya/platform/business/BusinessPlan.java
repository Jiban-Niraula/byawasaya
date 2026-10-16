package com.byawasaya.platform.business;

import java.math.BigDecimal;

public enum BusinessPlan {
	STARTER("Starter", new BigDecimal("2500.00")),
	PLUS("Plus", new BigDecimal("3500.00")),
	PRO("Pro", new BigDecimal("5000.00")),
	/** Legacy values retained so existing installations can migrate without data loss. */
	@Deprecated PROFESSIONAL("Plus", new BigDecimal("3500.00")),
	@Deprecated ENTERPRISE("Pro", new BigDecimal("5000.00"));

	private final String displayName;
	private final BigDecimal monthlyPriceNpr;

	BusinessPlan(String displayName, BigDecimal monthlyPriceNpr) {
		this.displayName = displayName;
		this.monthlyPriceNpr = monthlyPriceNpr;
	}

	public String getDisplayName() { return displayName; }
	public BigDecimal getMonthlyPriceNpr() { return monthlyPriceNpr; }
}
