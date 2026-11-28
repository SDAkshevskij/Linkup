const HOUR = 3_600_000;

export function dateKey(date) {
    const year = String(date.getFullYear()).padStart(4, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function calendarStep(key, step) {
    const date = new Date(key + 'T00:00:00Z');
    date.setUTCDate(date.getUTCDate() + step);
    const next = date.toISOString().split('T')[0];
    return /^\d{4}-\d{2}-\d{2}$/.test(next) ? next : null;
}

export function localDay(key) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(key)) return null;
    const from = new Date(key + 'T00:00:00');
    if (!Number.isFinite(+from) || dateKey(from) !== key) return null;
    const next = calendarStep(key, 1);
    if (!next) return null;
    // Resolve the next date independently. A clock change can move this day's
    // first instant to 01:00, or remove the next calendar date altogether.
    const to = +new Date(next + 'T00:00:00');
    return to > +from ? {key, from: +from, to} : null;
}

export function stepDay(day, direction) {
    let key = calendarStep(day.key, direction);
    while (key) {
        const range = localDay(key);
        if (range) return range;
        key = calendarStep(key, direction);
    }
    return null;
}

export function dayScale({from, to}) {
    const cells = [];
    for (let start = from; start < to; start += HOUR) {
        cells.push({start, end: Math.min(start + HOUR, to)});
    }
    const ticks = cells.filter(({start}, index) => index % 6 === 0 && to - start >= 4 * HOUR)
        .map(({start}) => start);
    return {cells, ticks: [...ticks, to]};
}

export function clipToDay(rows, {from, to}) {
    return rows.flatMap(row => {
        if (!row) return [];
        const {start_ms: start, end_ms: end} = row;
        if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return [];
        if (start === end) return start >= from && start < to ? [row] : [];
        if (start >= to || end <= from) return [];
        return [{...row, start_ms: Math.max(from, start), end_ms: Math.min(to, end)}];
    });
}
