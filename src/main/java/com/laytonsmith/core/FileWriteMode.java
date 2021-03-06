package com.laytonsmith.core;

import com.laytonsmith.PureUtilities.Version;
import com.laytonsmith.annotations.MEnum;

/**
 * Defines the common file write modes available.
 */
@MEnum(value = "ms.lang.FileWriteMode")
public enum FileWriteMode implements SimpleDocumentation {
	OVERWRITE("Overwrites the content in the given file.", MSVersion.V3_3_4),
	APPEND("Appends the content to the existing file.", MSVersion.V3_3_4),
	SAFE_WRITE("Writes the content, but only if the file does not currently exist. If the file exists, an"
			+ " IOException is thrown.", MSVersion.V3_3_4);
	final String docs;
	final Version since;

	private FileWriteMode(String docs, Version since) {
		this.docs = docs;
		this.since = since;
	}

	@Override
	public String docs() {
		return docs;
	}

	@Override
	public Version since() {
		return since;
	}

	@Override
	public String getName() {
		return name();
	}

}
