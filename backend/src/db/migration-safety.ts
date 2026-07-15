export function createdTableNames(sql: string): string[] {
  const names = new Set<string>();
  const pattern = /CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([A-Za-z0-9_]+)`?/gi;
  for (const match of sql.matchAll(pattern)) {
    const name = match[1];
    if (name) names.add(name);
  }
  return [...names];
}

export function untrackedTableConflicts(migrationSql: string, existingTables: Iterable<string>): string[] {
  const existing = new Set([...existingTables].map((name) => name.toLowerCase()));
  return createdTableNames(migrationSql).filter((name) => existing.has(name.toLowerCase()));
}
