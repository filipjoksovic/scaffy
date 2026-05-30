package com.scaffy.backend.workspace;

public final class WorkspaceRole {

	public static final String OWNER = "owner";
	public static final String MEMBER = "member";

	private WorkspaceRole() {
	}

	public static boolean isOwner(String role) {
		return OWNER.equals(role);
	}

	public static String normalize(String role) {
		return OWNER.equalsIgnoreCase(role) ? OWNER : MEMBER;
	}
}
