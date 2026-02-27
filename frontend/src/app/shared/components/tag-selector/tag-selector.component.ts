import { Component, Input, Output, EventEmitter, OnInit, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TagService } from '../../../core/services';
import { Tag } from '../../../core/models';

@Component({
  selector: 'app-tag-selector',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './tag-selector.component.html',
  styleUrl: './tag-selector.component.scss'
})
export class TagSelectorComponent implements OnInit {
  @Input() selectedTags: Tag[] = [];
  @Output() tagsChanged = new EventEmitter<Tag[]>();
  @Output() closed = new EventEmitter<void>();

  @ViewChild('searchInput') searchInput!: ElementRef<HTMLInputElement>;

  allTags: Tag[] = [];
  searchTerm = '';
  creatingTag = false;

  constructor(private tagService: TagService) {}

  ngOnInit(): void {
    this.loadTags();
  }

  private loadTags(): void {
    this.tagService.list().subscribe({
      next: tags => this.allTags = tags,
      error: () => this.allTags = []
    });
  }

  get filteredTags(): Tag[] {
    if (!this.searchTerm.trim()) return this.allTags;
    const term = this.searchTerm.toLowerCase();
    return this.allTags.filter(t => t.name.toLowerCase().includes(term));
  }

  get showCreateOption(): boolean {
    if (!this.searchTerm.trim()) return false;
    const term = this.searchTerm.trim().toLowerCase();
    return !this.allTags.some(t => t.name.toLowerCase() === term);
  }

  isSelected(tag: Tag): boolean {
    return this.selectedTags.some(t => t.id === tag.id);
  }

  toggleTag(tag: Tag): void {
    if (this.isSelected(tag)) {
      this.tagsChanged.emit(this.selectedTags.filter(t => t.id !== tag.id));
    } else {
      this.tagsChanged.emit([...this.selectedTags, tag]);
    }
  }

  createTag(): void {
    const name = this.searchTerm.trim();
    if (!name || this.creatingTag) return;
    this.creatingTag = true;
    this.tagService.create(name).subscribe({
      next: tag => {
        this.allTags = [...this.allTags, tag];
        this.tagsChanged.emit([...this.selectedTags, tag]);
        this.searchTerm = '';
        this.creatingTag = false;
      },
      error: () => this.creatingTag = false
    });
  }

  removeTag(tag: Tag): void {
    this.tagsChanged.emit(this.selectedTags.filter(t => t.id !== tag.id));
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('tag-selector-backdrop')) {
      this.closed.emit();
    }
  }

  onSearchKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      this.closed.emit();
    } else if (event.key === 'Enter' && this.showCreateOption) {
      this.createTag();
    }
  }
}
