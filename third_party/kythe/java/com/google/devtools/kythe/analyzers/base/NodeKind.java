package com.google.devtools.kythe.analyzers.base;

/** Schema-defined Kythe node kinds. */
public enum NodeKind {
  // Core kinds
  ANCHOR("anchor"),
  NAME("name"),
  FILE("file"),
  FUNCTION("function"),
  PACKAGE("package"),
  TAPPLY("tapp"),
  TBUILTIN("tbuiltin"),
  VARIABLE("variable"),

  // Sub-kinds
  RECORD_CLASS("record", "class"),
  RECORD_STRUCT("record", "struct"),
  SUM_ENUM_CLASS("sum", "enumClass");

  private final String kind, subkind;
  NodeKind(String kind) {
    this(kind, null);
  }
  NodeKind(String kind, String subkind) {
    this.kind = kind;
    this.subkind = subkind;
  }

  /** Returns the node's kind Kythe GraphStore value. */
  public final String getKind() {
    return kind;
  }

  /** Returns the node's subkind Kythe GraphStore value (or {@code null}). */
  public final String getSubkind() {
    return subkind;
  }

  @Override
  public String toString() {
    return kind + (subkind == null ? "" : "/" + subkind);
  }
}
