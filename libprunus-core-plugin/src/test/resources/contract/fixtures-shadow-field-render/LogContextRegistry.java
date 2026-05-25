package contract;

import org.libprunus.core.log.annotation.LogRegistry;
import org.libprunus.core.log.annotation.MaxMessageLength;
import org.libprunus.core.log.annotation.ToStringProfile;

@LogRegistry
@MaxMessageLength(4096)
@ToStringProfile(
        includePackages = "contract",
        includeClassSuffixes = {"Dto", "Inner", "Record"})
public class LogContextRegistry {}
