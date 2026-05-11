export function createLecturePlaybackEngine({
  getSlides,
  getCurrentPage,
  syncToPage,
  playCurrentPage,
  stopPlayback,
  setLectureStatus,
  endedStatus,
  onContinuousPlaybackChange,
}) {
  let continuousPlayback = false

  const setContinuous = enabled => {
    continuousPlayback = Boolean(enabled)
    onContinuousPlaybackChange?.(continuousPlayback)
  }

  const getLastPage = () => {
    const slides = getSlides()
    return Array.isArray(slides) ? slides.length : 0
  }

  const hasNextPage = () => getCurrentPage() < getLastPage()

  const playPage = async pageIndex => {
    const targetPage = Number(pageIndex)
    if (!Number.isFinite(targetPage) || targetPage <= 0 || targetPage > getLastPage()) {
      return false
    }

    setContinuous(true)
    stopPlayback()
    syncToPage(targetPage)
    await playCurrentPage(targetPage)
    return true
  }

  const nextPage = async (autoPlay = true) => {
    if (!hasNextPage()) {
      if (autoPlay) {
        finishLecture()
      }
      return false
    }

    const next = getCurrentPage() + 1
    stopPlayback()
    syncToPage(next)
    setContinuous(autoPlay)

    if (autoPlay) {
      await playCurrentPage(next)
    }

    return true
  }

  const previousPage = async (autoPlay = false) => {
    const currentPage = getCurrentPage()
    const previous = Math.max(1, currentPage - 1)
    stopPlayback()
    syncToPage(previous)
    setContinuous(autoPlay)

    if (autoPlay) {
      await playCurrentPage(previous)
    }

    return previous !== currentPage
  }

  const stopCurrentPage = () => {
    setContinuous(false)
    stopPlayback()
  }

  const finishLecture = () => {
    setContinuous(false)
    stopPlayback()
    setLectureStatus(endedStatus)
  }

  const handlePlaybackEnded = async () => {
    if (!continuousPlayback) {
      return false
    }

    if (!hasNextPage()) {
      finishLecture()
      return true
    }

    await nextPage(true)
    return true
  }

  const setContinuousPlayback = enabled => {
    setContinuous(enabled)
  }

  const isContinuousPlayback = () => continuousPlayback

  return {
    playPage,
    nextPage,
    previousPage,
    stopCurrentPage,
    finishLecture,
    handlePlaybackEnded,
    setContinuousPlayback,
    isContinuousPlayback,
  }
}
