// The OCS searcher response (search-api/v1/search/{tenant}) as the edge proxies it on GET /api/search.
export interface OcsDocument { id: string; data: Record<string, unknown> }
export interface OcsHit { index: string; document: OcsDocument; matchedQueries?: string[]; metaData?: { score?: number } }
export interface OcsFacetEntry {
  type: 'term' | 'interval' | 'range' | 'hierarchical';
  key: string;
  id?: string | null;
  docCount: number;
  link?: string;
  selected: boolean;
  lowerBound?: number;
  upperBound?: number;
}
export interface OcsFacet {
  fieldName: string;
  absoluteFacetCoverage: number;
  isFiltered: boolean;
  entries: OcsFacetEntry[];
  type: 'term' | 'interval' | 'range' | 'hierarchical';
  meta?: { label?: string; multiSelect?: boolean; order?: number };
}
export interface OcsSlice { label: string; matchCount: number; nextOffset?: number | null; hits: OcsHit[]; facets: OcsFacet[] }
export interface OcsSortOption { label: string; field: string; sortOrder: 'ASC' | 'DESC'; isActive?: boolean; active?: boolean; link?: string }
export interface OcsSearchResult { tookInMillis: number; inputURI: string; slices: OcsSlice[]; sortOptions: OcsSortOption[]; meta?: Record<string, unknown> }

/** One suggestion of the OCS suggest service. */
export interface OcsSuggestion { phrase: string; type?: string; payload?: Record<string, unknown> }

/** The normalised card the search page renders from a hit of either source. */
export interface SearchHit {
  id: string;
  source: 'local' | 'dba' | 'tradera' | string;
  kind: string;
  title: string;
  description?: string;
  price?: number;
  currency?: string;
  saleType?: string;
  imageUrl?: string;
  city?: string;
  postalCode?: string;
  externalUrl?: string;
  createdEpoch?: number;
  endsEpoch?: number;
  employer?: string;
  contractType?: string;
  startsEpoch?: number;
  data: Record<string, unknown>;
}
