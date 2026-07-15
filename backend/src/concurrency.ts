export class ConcurrencyGate {
  private active = 0;

  constructor(private readonly capacity: number) {
    if (!Number.isInteger(capacity) || capacity < 1) throw new Error("ConcurrencyGate capacity must be a positive integer");
  }

  tryAcquire(): (() => void) | null {
    if (this.active >= this.capacity) return null;
    this.active += 1;
    let released = false;
    return () => {
      if (released) return;
      released = true;
      this.active -= 1;
    };
  }

  get inUse(): number {
    return this.active;
  }
}
