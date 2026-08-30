package com.meridian.keystone.dto;

import java.util.List;

/** Kanban board: one column per lifecycle status, already scoped to the caller. */
public record BoardView(List<BoardColumn> columns) {
}
