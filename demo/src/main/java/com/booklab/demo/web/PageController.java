package com.booklab.demo.web;

import com.booklab.demo.domain.Page;
import com.booklab.demo.domain.PageInputType;
import com.booklab.demo.domain.PageStatus;
import com.booklab.demo.repo.PageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PageController {

    private final PageRepository pageRepo;

    public PageController(PageRepository pageRepo) {
        this.pageRepo = pageRepo;
    }

    @GetMapping("/pages/{id}")
    public String viewPage(@PathVariable Long id,
                           Model model,
                           @ModelAttribute("msg") String msg,
                           @ModelAttribute("err") String err) {
        Page page = pageRepo.findWithDocumentById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page introuvable"));

        boolean isTextPage = page.getInputType() == PageInputType.TEXT;

        model.addAttribute("page", page);
        model.addAttribute("docId", page.getDocument().getId());
        model.addAttribute("isTextPage", isTextPage);
        model.addAttribute("msg", (msg != null && !msg.isBlank()) ? msg : null);
        model.addAttribute("err", (err != null && !err.isBlank()) ? err : null);
        return "page";
    }

    @PostMapping("/pages/{id}/edit")
    public String editPage(@PathVariable Long id,
                           @RequestParam(required = false) String hebrewInputText,
                           @RequestParam(required = false) String hebrewPlain,
                           @RequestParam(required = false) String hebrewNikud,
                           @RequestParam(required = false) String frText,
                           RedirectAttributes ra) {

        Page page = pageRepo.findWithDocumentById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page introuvable"));

        if (page.getInputType() != PageInputType.TEXT) {
            ra.addFlashAttribute("err", "Seules les pages TEXT sont éditables.");
            return "redirect:/pages/" + id;
        }

        page.setHebrewInputText(nullIfBlank(hebrewInputText));
        page.setHebrewPlain(nullIfBlank(hebrewPlain));
        page.setHebrewNikud(nullIfBlank(hebrewNikud));
        page.setFrText(nullIfBlank(frText));

        // si tu saisis manuellement, on considère DONE (utile pour PDF)
        page.setStatus(PageStatus.DONE);
        page.setError(null);

        pageRepo.save(page);

        ra.addFlashAttribute("msg", "Page enregistrée.");
        return "redirect:/pages/" + id;
    }

    private static String nullIfBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : s;
    }
}
